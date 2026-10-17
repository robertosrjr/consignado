mock_provider "aws" {
  mock_resource "aws_sns_topic" {
    defaults = {
      arn = "arn:aws:sns:sa-east-1:247755251743:alert-consignado"
    }
  }
  mock_resource "aws_sqs_queue" {
    defaults = {
      arn = "arn:aws:sqs:sa-east-1:247755251743:alert-consignado-sqs"
      url = "https://sqs.sa-east-1.amazonaws.com/247755251743/alert-consignado-sqs"
    }
  }
}

variables {
  ec2_config = {
    ami = "ami-00000000000000001", instance_type = "t3.medium", subnet_id = "subnet-00000000000000001"
    security_group_ids = ["sg-00000000000000001"], key_name = "test"
    associate_public_ip_address = true, source_dest_check = true, ebs_optimized = true
    monitoring = false, tags = {}, http_endpoint = "enabled", http_tokens = "required"
    http_put_response_hop_limit = 2, http_protocol_ipv6 = "disabled", instance_metadata_tags = "disabled"
  }
  publish_policy_name = "publish-alerts"
  assume_role_policy_json = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
  existing_queue_policy_json = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "ExistingConsumer"
      Effect    = "Allow"
      Principal = { AWS = "arn:aws:iam::247755251743:role/existing-consumer" }
      Action    = "sqs:ReceiveMessage"
      Resource  = "arn:aws:sqs:sa-east-1:247755251743:alert-consignado-sqs"
    }]
  })
}

run "preserve_existing_policy_and_limit_new_permissions" {
  command = plan

  assert {
    condition     = length(jsondecode(aws_sqs_queue_policy.sns_delivery.policy).Statement) == 2
    error_message = "A policy existente deve ser preservada junto da permissao SNS."
  }
  assert {
    condition     = jsondecode(aws_sqs_queue_policy.sns_delivery.policy).Statement[0].Sid == "ExistingConsumer"
    error_message = "O consumidor existente nao pode perder sua permissao."
  }
  assert {
    condition     = jsondecode(aws_sqs_queue_policy.sns_delivery.policy).Statement[1].Condition.ArnEquals["aws:SourceArn"] == "arn:aws:sns:sa-east-1:247755251743:alert-consignado"
    error_message = "Somente o topico informado pode enviar mensagens."
  }
  assert {
    condition     = jsondecode(aws_sqs_queue_policy.sns_delivery.policy).Statement[1].Principal.Service == "sns.amazonaws.com"
    error_message = "A permissao de entrega precisa ser concedida ao SNS."
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.sns_publish.policy).Statement[0].Action == "sns:Publish" && jsondecode(aws_iam_role_policy.sns_publish.policy).Statement[0].Resource == "arn:aws:sns:sa-east-1:247755251743:alert-consignado"
    error_message = "O Alertmanager deve somente publicar no topico autorizado."
  }
  assert {
    condition     = aws_sns_topic_subscription.alerts_queue.protocol == "sqs" && aws_sns_topic_subscription.alerts_queue.endpoint == "arn:aws:sqs:sa-east-1:247755251743:alert-consignado-sqs"
    error_message = "A subscription deve entregar para a fila informada."
  }
}

run "managed_sid_is_not_duplicated" {
  command = plan
  variables {
    existing_queue_policy_json = jsonencode({
      Version   = "2012-10-17"
      Statement = [{ Sid = "AllowConsignadoSns", Effect = "Allow", Principal = "*", Action = "sqs:*", Resource = "*" }]
    })
  }
  assert {
    condition     = length(jsondecode(aws_sqs_queue_policy.sns_delivery.policy).Statement) == 1 && jsondecode(aws_sqs_queue_policy.sns_delivery.policy).Statement[0].Action == "sqs:SendMessage"
    error_message = "O Sid gerenciado deve ser atualizado com menor privilegio, sem duplicacao."
  }
}
