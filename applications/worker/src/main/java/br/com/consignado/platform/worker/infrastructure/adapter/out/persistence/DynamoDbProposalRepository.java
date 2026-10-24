package br.com.consignado.platform.worker.infrastructure.adapter.out.persistence;

import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.out.ClockPort;
import br.com.consignado.platform.worker.application.port.out.CreationResult;
import br.com.consignado.platform.worker.application.port.out.ProposalRepositoryPort;
import br.com.consignado.platform.worker.application.port.out.ProposalSeed;
import br.com.consignado.platform.worker.domain.Proposal;
import br.com.consignado.platform.worker.domain.ProposalEvent;
import br.com.consignado.platform.worker.domain.ProposalStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * Estado duravel do Worker no DynamoDB (S5). Particao por proposta: item {@code STATE} e itens
 * {@code EVENT#...} do historico, gravados na mesma transacao. Mesmas garantias do repositorio em
 * memoria: criacao idempotente e estado terminal que nunca regride ({@code final = false AND rank
 * <= :rank} na condicao). Sobrevive ao reinicio do processo.
 *
 * <p>{@link #count} e local ao processo (transicoes que esta instancia gravou), suficiente para o
 * painel; nao e inventario da tabela.
 */
public final class DynamoDbProposalRepository
    implements ProposalRepositoryPort, ProposalStatusCounts {

  static final String PK = "proposalKey";
  static final String SK = "sk";
  static final String STATE = "STATE";

  private final DynamoDbClient dynamo;
  private final String tableName;
  private final ClockPort clock;
  private final Map<ProposalStatus, AtomicLong> counts = new EnumMap<>(ProposalStatus.class);

  public DynamoDbProposalRepository(DynamoDbClient dynamo, String tableName, ClockPort clock) {
    this.dynamo = dynamo;
    this.tableName = tableName;
    this.clock = clock;
    for (ProposalStatus status : ProposalStatus.values()) {
      counts.put(status, new AtomicLong());
    }
  }

  @Override
  public CreationResult createPendingIfAbsent(ProposalSeed seed) {
    Instant now = clock.now();
    Proposal pending = Proposal.pending(seed.key(), seed.tenant(), seed.lane(), now);
    List<TransactWriteItem> writes = new ArrayList<>();
    writes.add(
        TransactWriteItem.builder()
            .put(
                Put.builder()
                    .tableName(tableName)
                    .item(stateItem(pending))
                    .conditionExpression("attribute_not_exists(#pk)")
                    .expressionAttributeNames(Map.of("#pk", PK))
                    .build())
            .build());
    writes.add(
        eventWrite(
            ProposalEvent.of(seed.key(), ProposalEvent.CREATED, now, "lane=" + seed.lane()), 0));
    if (transact(writes)) {
      counts.get(ProposalStatus.PENDING).incrementAndGet();
      return new CreationResult(pending, true);
    }
    return new CreationResult(
        find(seed.key()).orElseThrow(() -> new IllegalStateException("Proposta inconsistente")),
        false);
  }

  @Override
  public void save(Proposal proposal, List<ProposalEvent> events) {
    List<TransactWriteItem> writes = new ArrayList<>();
    writes.add(
        TransactWriteItem.builder()
            .put(
                Put.builder()
                    .tableName(tableName)
                    .item(stateItem(proposal))
                    .conditionExpression(
                        "attribute_exists(#pk) AND #final = :no AND #rank <= :rank")
                    .expressionAttributeNames(Map.of("#pk", PK, "#final", "final", "#rank", "rank"))
                    .expressionAttributeValues(
                        Map.of(
                            ":no", AttributeValue.builder().bool(false).build(),
                            ":rank", n(proposal.status().ordinal())))
                    .build())
            .build());
    for (int i = 0; i < events.size(); i++) {
      writes.add(eventWrite(events.get(i), i));
    }
    boolean resend = proposal.status() == ProposalStatus.SENT_TO_LEGACY && proposal.attempts() > 1;
    if (transact(writes) && !resend) {
      counts.get(proposal.status()).incrementAndGet();
      ProposalStatus previous = previousOf(proposal);
      if (previous != null) {
        counts.get(previous).decrementAndGet();
      }
    }
    // condicao falhou: terminal ou regressao; ignorado como no repositorio em memoria
  }

  @Override
  public Optional<Proposal> find(IdempotencyKey key) {
    Map<String, AttributeValue> item;
    try {
      item =
          dynamo
              .getItem(
                  GetItemRequest.builder()
                      .tableName(tableName)
                      .consistentRead(true)
                      .key(Map.of(PK, s(key.toString()), SK, s(STATE)))
                      .build())
              .item();
    } catch (SdkException e) {
      throw new IllegalStateException("Estado do Worker indisponivel para leitura", e);
    }
    if (item == null || item.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Proposal(
            key,
            new TenantId(item.get("tenant").s()),
            Lane.valueOf(item.get("lane").s()),
            ProposalStatus.valueOf(item.get("status").s()),
            Integer.parseInt(item.get("attempts").n()),
            text(item, "averbacaoId"),
            text(item, "failureReason"),
            Instant.parse(item.get("createdAt").s()),
            Instant.parse(item.get("updatedAt").s())));
  }

  /** Historico gravado, em ordem (para testes e diagnostico). */
  public List<ProposalEvent> history(IdempotencyKey key) {
    List<ProposalEvent> events = new ArrayList<>();
    for (Map<String, AttributeValue> item :
        dynamo
            .query(
                QueryRequest.builder()
                    .tableName(tableName)
                    .consistentRead(true)
                    .keyConditionExpression("#pk = :pk AND begins_with(#sk, :event)")
                    .expressionAttributeNames(Map.of("#pk", PK, "#sk", SK))
                    .expressionAttributeValues(
                        Map.of(":pk", s(key.toString()), ":event", s("EVENT#")))
                    .build())
            .items()) {
      events.add(
          ProposalEvent.of(
              key,
              item.get("type").s(),
              Instant.parse(item.get("occurredAt").s()),
              text(item, "detail")));
    }
    return events;
  }

  @Override
  public long count(ProposalStatus status) {
    return counts.get(status).get();
  }

  private boolean transact(List<TransactWriteItem> writes) {
    try {
      dynamo.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(writes).build());
      return true;
    } catch (TransactionCanceledException e) {
      if (e.hasCancellationReasons()
          && e.cancellationReasons().stream()
              .map(CancellationReason::code)
              .anyMatch("ConditionalCheckFailed"::equals)) {
        return false;
      }
      throw new IllegalStateException("Transacao do estado do Worker cancelada", e);
    } catch (SdkException e) {
      throw new IllegalStateException("Estado do Worker indisponivel para escrita", e);
    }
  }

  private TransactWriteItem eventWrite(ProposalEvent event, int index) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put(PK, s(event.key().toString()));
    // ordenavel por instante; o indice desempata eventos do mesmo milissegundo
    item.put(
        SK,
        s("EVENT#%013d#%02d#%s".formatted(event.occurredAt().toEpochMilli(), index, event.type())));
    item.put("type", s(event.type()));
    item.put("occurredAt", s(event.occurredAt().toString()));
    if (event.detail() != null) {
      item.put("detail", s(event.detail()));
    }
    return TransactWriteItem.builder()
        .put(Put.builder().tableName(tableName).item(item).build())
        .build();
  }

  private static Map<String, AttributeValue> stateItem(Proposal proposal) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put(PK, s(proposal.key().toString()));
    item.put(SK, s(STATE));
    item.put("tenant", s(proposal.tenant().value()));
    item.put("lane", s(proposal.lane().name()));
    item.put("status", s(proposal.status().name()));
    item.put("rank", n(proposal.status().ordinal()));
    item.put("final", AttributeValue.builder().bool(proposal.isFinal()).build());
    item.put("attempts", n(proposal.attempts()));
    if (proposal.averbacaoId() != null) {
      item.put("averbacaoId", s(proposal.averbacaoId()));
    }
    if (proposal.failureReason() != null) {
      item.put("failureReason", s(proposal.failureReason()));
    }
    item.put("createdAt", s(proposal.createdAt().toString()));
    item.put("updatedAt", s(proposal.updatedAt().toString()));
    return item;
  }

  /** Estado anterior pela maquina de estados (para o contador local). */
  private static ProposalStatus previousOf(Proposal proposal) {
    return switch (proposal.status()) {
      case PENDING -> null;
      case SENT_TO_LEGACY -> ProposalStatus.PENDING;
      case CONFIRMED, FAILED -> ProposalStatus.SENT_TO_LEGACY;
    };
  }

  private static String text(Map<String, AttributeValue> item, String name) {
    AttributeValue value = item.get(name);
    return value == null ? null : value.s();
  }

  private static AttributeValue s(String value) {
    return AttributeValue.builder().s(value).build();
  }

  private static AttributeValue n(long value) {
    return AttributeValue.builder().n(Long.toString(value)).build();
  }
}
