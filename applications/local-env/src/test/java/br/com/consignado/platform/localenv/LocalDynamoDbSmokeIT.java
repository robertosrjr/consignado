package br.com.consignado.platform.localenv;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.shared.contract.InboxAttributes;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * Verifica que o DynamoDB Local embarcado sobe nesta maquina (Windows, JDK 25) e grava/le itens.
 */
class LocalDynamoDbSmokeIT {

  @Test
  void should_start_create_the_inbox_table_and_round_trip_an_item() throws Exception {
    try (LocalDynamoDb dynamo = LocalDynamoDb.start(0);
        DynamoDbClient client = dynamo.client()) {
      dynamo.createInboxTable(client, InboxAttributes.TABLE_NAME);
      Map<String, AttributeValue> key =
          Map.of(InboxAttributes.IDEMPOTENCY_KEY, AttributeValue.builder().s("k-1").build());
      client.putItem(
          PutItemRequest.builder()
              .tableName(InboxAttributes.TABLE_NAME)
              .item(
                  Map.of(
                      InboxAttributes.IDEMPOTENCY_KEY, AttributeValue.builder().s("k-1").build(),
                      InboxAttributes.TENANT_ID, AttributeValue.builder().s("banco-a").build()))
              .build());

      var item =
          client
              .getItem(
                  GetItemRequest.builder()
                      .tableName(InboxAttributes.TABLE_NAME)
                      .key(key)
                      .consistentRead(true)
                      .build())
              .item();

      assertThat(item.get(InboxAttributes.TENANT_ID).s()).isEqualTo("banco-a");
    }
  }
}
