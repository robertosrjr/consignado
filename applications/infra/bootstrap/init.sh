#!/bin/sh
# Provisionamento local idempotente: tabela DurableInbox (Streams + TTL), validacao das filas e
# chave KMS de teste (alias). Executado uma vez pelo servico `bootstrap` do Compose.
set -eu

DDB="--endpoint-url ${DYNAMODB_ENDPOINT}"
SQS="--endpoint-url ${SQS_ENDPOINT}"
KMS="--endpoint-url ${KMS_ENDPOINT}"
KEY_ALIAS="alias/consignado-poc-payload"

echo "[bootstrap] DynamoDB: DurableInbox"
if ! aws dynamodb describe-table --table-name DurableInbox $DDB >/dev/null 2>&1; then
  aws dynamodb create-table \
    --table-name DurableInbox \
    --attribute-definitions AttributeName=idempotencyKey,AttributeType=S \
    --key-schema AttributeName=idempotencyKey,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --stream-specification StreamEnabled=true,StreamViewType=NEW_IMAGE \
    $DDB >/dev/null
fi
aws dynamodb update-time-to-live \
  --table-name DurableInbox \
  --time-to-live-specification Enabled=true,AttributeName=ttl \
  $DDB >/dev/null

echo "[bootstrap] SQS: validando filas definidas em elasticmq.conf"
for queue in sqs-fast-lane-queue sqs-standard-lane-queue sqs-dlq; do
  aws sqs get-queue-url --queue-name "$queue" $SQS >/dev/null
done

echo "[bootstrap] KMS: chave de teste ${KEY_ALIAS}"
if ! aws kms describe-key --key-id "$KEY_ALIAS" $KMS >/dev/null 2>&1; then
  KEY_ID=$(aws kms create-key --description consignado-poc-payload \
    --query KeyMetadata.KeyId --output text $KMS)
  aws kms create-alias --alias-name "$KEY_ALIAS" --target-key-id "$KEY_ID" $KMS
fi

echo "[bootstrap] concluido"
