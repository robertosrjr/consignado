package br.com.consignado.platform.worker.infrastructure.adapter.out.persistence;

import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.worker.application.port.out.ClockPort;
import br.com.consignado.platform.worker.application.port.out.CreationResult;
import br.com.consignado.platform.worker.application.port.out.ProposalRepositoryPort;
import br.com.consignado.platform.worker.application.port.out.ProposalSeed;
import br.com.consignado.platform.worker.domain.Proposal;
import br.com.consignado.platform.worker.domain.ProposalEvent;
import br.com.consignado.platform.worker.domain.ProposalStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repositorio em memoria (primeira fase). Estado e eventos sao gravados de forma atomica por chave
 * ({@code ConcurrentHashMap.compute}), e um estado terminal nunca regride. Os dados se perdem ao
 * reiniciar o processo; o DDL de referencia do SDD (proposal, proposal_event) fica para a fase com
 * banco relacional, trocando apenas este adaptador.
 */
public final class InMemoryProposalRepository implements ProposalRepositoryPort {

  private final ClockPort clock;
  private final Map<IdempotencyKey, Instant> terminalCommittedAt = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<IdempotencyKey, Proposal> proposals = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<IdempotencyKey, List<ProposalEvent>> events =
      new ConcurrentHashMap<>();
  private final Map<ProposalStatus, AtomicLong> countByStatus = new EnumMap<>(ProposalStatus.class);

  public InMemoryProposalRepository(ClockPort clock) {
    this.clock = clock;
    for (ProposalStatus status : ProposalStatus.values()) {
      countByStatus.put(status, new AtomicLong());
    }
  }

  @Override
  public synchronized CreationResult createPendingIfAbsent(ProposalSeed seed) {
    AtomicBoolean created = new AtomicBoolean(false);
    Proposal proposal =
        proposals.compute(
            seed.key(),
            (key, existing) -> {
              if (existing != null) {
                return existing;
              }
              created.set(true);
              Proposal pending = Proposal.pending(key, seed.tenant(), seed.lane(), clock.now());
              countByStatus.get(ProposalStatus.PENDING).incrementAndGet();
              eventsOf(key)
                  .add(
                      ProposalEvent.of(
                          key, ProposalEvent.CREATED, pending.createdAt(), "lane=" + seed.lane()));
              return pending;
            });
    return new CreationResult(proposal, created.get());
  }

  @Override
  public synchronized void save(Proposal proposal, List<ProposalEvent> newEvents) {
    proposals.compute(
        proposal.key(),
        (key, existing) -> {
          if (existing != null && existing.isFinal()) {
            return existing; // estado terminal nunca regride; eventos descartados
          }
          if (existing != null && proposal.status().ordinal() < existing.status().ordinal()) {
            return existing;
          }
          if (existing != null) {
            countByStatus.get(existing.status()).decrementAndGet();
          }
          countByStatus.get(proposal.status()).incrementAndGet();
          if (proposal.isFinal()) terminalCommittedAt.put(key, clock.now());
          eventsOf(key).addAll(newEvents);
          return proposal;
        });
  }

  @Override
  public Optional<Proposal> find(IdempotencyKey key) {
    return Optional.ofNullable(proposals.get(key));
  }

  /** Historico de eventos da proposta, em ordem de gravacao (copia imutavel). */
  public List<ProposalEvent> history(IdempotencyKey key) {
    List<ProposalEvent> list = events.get(key);
    return list == null ? List.of() : List.copyOf(new ArrayList<>(list));
  }

  /** Quantidade de propostas no estado informado (base da metrica {@code worker.proposals}). */
  public long count(ProposalStatus status) {
    return countByStatus.get(status).get();
  }

  public int size() {
    return proposals.size();
  }

  /** Consistent, local-only measurement cut. No payload, tenant, financial value or trace data. */
  public synchronized CaptureSnapshot captureSnapshot() {
    Instant observedAt = clock.now();
    List<CaptureState> states =
        proposals.values().stream()
            .map(
                proposal ->
                    new CaptureState(
                        proposal.key().toString(),
                        proposal.lane().name().toLowerCase(java.util.Locale.ROOT),
                        proposal.status().name(),
                        terminalCommittedAt.get(proposal.key()) == null
                            ? null
                            : terminalCommittedAt.get(proposal.key()).toString()))
            .sorted(java.util.Comparator.comparing(CaptureState::canonicalId))
            .toList();
    return new CaptureSnapshot(observedAt.toString(), states);
  }

  public record CaptureState(String canonicalId, String lane, String status, String committedAt) {}

  public record CaptureSnapshot(String observedAt, List<CaptureState> states) {}

  private List<ProposalEvent> eventsOf(IdempotencyKey key) {
    return events.computeIfAbsent(
        key, k -> Collections.synchronizedList(new CopyOnWriteArrayList<>()));
  }
}
