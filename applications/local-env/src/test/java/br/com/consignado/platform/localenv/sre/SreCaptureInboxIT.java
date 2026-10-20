package br.com.consignado.platform.localenv.sre;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.localenv.LocalDynamoDb;
import br.com.consignado.platform.shared.contract.InboxAttributes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.*;

class SreCaptureInboxIT {
  private static AttributeValue s(String text) {
    return AttributeValue.builder().s(text).build();
  }

  private static AttributeValue n(long value) {
    return AttributeValue.builder().n(Long.toString(value)).build();
  }

  @Test
  void readsOriginalInboxAndFingerprintAliasWithoutDecryptingPayloadAndRespectsRoutingCut()
      throws Exception {
    Instant received = Instant.parse("2026-10-17T12:00:00Z");
    String original = "11111111-1111-4111-8111-111111111111";
    String alias = "22222222-2222-4222-8222-222222222222";
    try (var local = LocalDynamoDb.start(0);
        var client = local.client()) {
      local.createInboxTable(client, InboxAttributes.TABLE_NAME);
      var item =
          new HashMap<>(
              Map.of(
                  "idempotencyKey",
                  s(original),
                  "tenantId",
                  s("bank"),
                  "createdAt",
                  n(received.toEpochMilli()),
                  "amount",
                  AttributeValue.builder().n("1234.56").build(),
                  "status",
                  s("DISPATCHED"),
                  "lane",
                  s("fast"),
                  "routedAt",
                  n(received.plusSeconds(2).toEpochMilli()),
                  "payload",
                  s("not-a-valid-encrypted-payload")));
      client.putItem(
          PutItemRequest.builder().tableName(InboxAttributes.TABLE_NAME).item(item).build());
      var sample =
          new JtlCapture.Sample(
              "a1",
              original,
              "bank",
              original,
              received,
              50,
              202,
              true,
              "none",
              "RECEIVED",
              123456);
      var result = SreCaptureMain.readInbox(client, sample, false, received.plusSeconds(3));
      assertThat(result.amountCents()).isEqualTo(123456);
      assertThat(result.canonicalId()).isEqualTo(original);
      assertThat(result.lane()).isEqualTo("fast");
      assertThat(SreCaptureMain.readInbox(client, sample, false, received.plusSeconds(1)).lane())
          .isEqualTo("unrouted");
      client.createTable(
          CreateTableRequest.builder()
              .tableName("ProposalDeduplication")
              .attributeDefinitions(
                  AttributeDefinition.builder()
                      .attributeName("PK")
                      .attributeType(ScalarAttributeType.S)
                      .build(),
                  AttributeDefinition.builder()
                      .attributeName("SK")
                      .attributeType(ScalarAttributeType.S)
                      .build())
              .keySchema(
                  KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                  KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build())
              .billingMode(BillingMode.PAY_PER_REQUEST)
              .build());
      client.putItem(
          PutItemRequest.builder()
              .tableName("ProposalDeduplication")
              .item(
                  Map.of(
                      "PK",
                      s("HEADER#bank#" + alias),
                      "SK",
                      s("METADATA"),
                      "proposalId",
                      s(original)))
              .build());
      var replay =
          new JtlCapture.Sample(
              "a2",
              alias,
              "bank",
              original,
              received.plusSeconds(5),
              50,
              202,
              true,
              "none",
              "DUPLICATE_ACCEPTED",
              123456);
      assertThat(SreCaptureMain.readInbox(client, replay, true, received.plusSeconds(6)))
          .isEqualTo(result);
    }
  }
}
