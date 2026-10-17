locals {
  topic_arn = "arn:aws:sns:sa-east-1:247755251743:alert-consignado"
  queue_arn = "arn:aws:sqs:sa-east-1:247755251743:alert-consignado-sqs"

  sns_queue_statement = {
    Sid       = "AllowConsignadoSns"
    Effect    = "Allow"
    Principal = { Service = "sns.amazonaws.com" }
    Action    = "sqs:SendMessage"
    Resource  = local.queue_arn
    Condition = {
      ArnEquals    = { "aws:SourceArn" = local.topic_arn }
      StringEquals = { "aws:SourceAccount" = "247755251743" }
    }
  }
}

resource "aws_sns_topic" "alerts" {
  name              = "alert-consignado"
  fifo_topic        = false
  kms_master_key_id = var.sns_kms_master_key_id

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_sqs_queue" "alerts" {
  name                              = "alert-consignado-sqs"
  fifo_queue                        = false
  visibility_timeout_seconds        = var.visibility_timeout_seconds
  message_retention_seconds         = var.message_retention_seconds
  receive_wait_time_seconds         = var.receive_wait_time_seconds
  max_message_size                  = var.max_message_size
  sqs_managed_sse_enabled           = var.kms_master_key_id == null ? var.sqs_managed_sse_enabled : null
  kms_master_key_id                 = var.kms_master_key_id
  kms_data_key_reuse_period_seconds = var.kms_master_key_id == null ? null : var.kms_data_key_reuse_period_seconds
  redrive_policy                    = var.redrive_policy
  delay_seconds                     = var.delay_seconds

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_sqs_queue_policy" "sns_delivery" {
  queue_url = aws_sqs_queue.alerts.url
  # Mantem statements existentes; substitui somente o Sid gerenciado por este root.
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [for statement in jsondecode(var.existing_queue_policy_json).Statement : statement if try(statement.Sid, "") != "AllowConsignadoSns"],
      [local.sns_queue_statement]
    )
  })
}

resource "aws_sns_topic_subscription" "alerts_queue" {
  topic_arn            = aws_sns_topic.alerts.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.alerts.arn
  raw_message_delivery = var.raw_message_delivery
  filter_policy        = var.subscription_filter_policy
  filter_policy_scope  = var.subscription_filter_policy_scope
  redrive_policy       = var.subscription_redrive_policy

  depends_on = [aws_sqs_queue_policy.sns_delivery]

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_iam_role" "alertmanager" {
  name               = var.role_name
  path               = var.role_path
  assume_role_policy = var.assume_role_policy_json

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_iam_role_policy" "sns_publish" {
  name = var.publish_policy_name
  role = aws_iam_role.alertmanager.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "PublishConsignadoAlerts"
      Effect   = "Allow"
      Action   = "sns:Publish"
      Resource = local.topic_arn
    }]
  })
}

resource "aws_iam_instance_profile" "alertmanager" {
  name = var.instance_profile_name
  path = var.instance_profile_path
  role = aws_iam_role.alertmanager.name

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_instance" "observability" {
  ami                         = var.ec2_config.ami
  instance_type               = var.ec2_config.instance_type
  subnet_id                   = var.ec2_config.subnet_id
  vpc_security_group_ids      = var.ec2_config.security_group_ids
  key_name                    = var.ec2_config.key_name
  associate_public_ip_address = var.ec2_config.associate_public_ip_address
  source_dest_check           = var.ec2_config.source_dest_check
  ebs_optimized               = var.ec2_config.ebs_optimized
  monitoring                  = var.ec2_config.monitoring
  tags                        = var.ec2_config.tags
  iam_instance_profile = aws_iam_instance_profile.alertmanager.name

  metadata_options {
    http_endpoint               = var.ec2_config.http_endpoint
    http_tokens                 = var.ec2_config.http_tokens
    http_put_response_hop_limit = var.ec2_config.http_put_response_hop_limit
    http_protocol_ipv6          = var.ec2_config.http_protocol_ipv6
    instance_metadata_tags      = var.ec2_config.instance_metadata_tags
  }

  lifecycle {
    prevent_destroy = true
  }
}
