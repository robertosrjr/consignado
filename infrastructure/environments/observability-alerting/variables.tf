variable "instance_id" {
  type        = string
  description = "EC2 existente; este root nao cria nem substitui a instancia."
  default     = "i-082b5f183d18a8d25"
}

variable "role_name" {
  type    = string
  default = "consignado-observability-role"
}

variable "instance_profile_name" {
  type    = string
  default = "consignado-observability-role"
}

variable "publish_policy_name" {
  type        = string
  description = "Nome real da inline policy importada; obtido pelo preparar-importacao.ps1."
}

variable "existing_queue_policy_json" {
  type        = string
  description = "Policy atual integral da fila; preservada ao adicionar a permissao do SNS."

  validation {
    condition     = can(jsondecode(var.existing_queue_policy_json).Statement)
    error_message = "Informe um documento JSON com Statement (lista), mesmo que vazia."
  }
}

variable "assume_role_policy_json" {
  type        = string
  description = "Trust policy atual do role, preservada durante a importacao."
}

variable "raw_message_delivery" {
  type        = bool
  description = "Preservar o valor atual da subscription; true entrega o JSON sem envelope SNS."
  default     = false
}

variable "visibility_timeout_seconds" {
  type    = number
  default = 30
}

variable "message_retention_seconds" {
  type    = number
  default = 345600
}

variable "receive_wait_time_seconds" {
  type    = number
  default = 0
}

variable "max_message_size" {
  type    = number
  default = 1048576
}

variable "sqs_managed_sse_enabled" {
  type        = bool
  description = "Preservar SSE-SQS atual, sem trocar silenciosamente uma chave KMS existente."
  default     = true
}

variable "kms_master_key_id" {
  type    = string
  default = null
}

variable "kms_data_key_reuse_period_seconds" {
  type    = number
  default = 300
}

variable "redrive_policy" {
  type        = string
  description = "Redrive policy atual, se houver; nao inventa uma DLQ durante a importacao."
  default     = null
}

variable "sns_kms_master_key_id" {
  type    = string
  default = null
}

variable "role_path" {
  type    = string
  default = "/"
}

variable "instance_profile_path" {
  type    = string
  default = "/"
}

variable "subscription_filter_policy" {
  type    = string
  default = null
}

variable "subscription_filter_policy_scope" {
  type    = string
  default = null
}

variable "subscription_redrive_policy" {
  type    = string
  default = null
}

variable "delay_seconds" {
  type    = number
  default = 0
}

variable "ec2_config" {
  description = "Snapshot da instancia existente, gerado antes da importacao. Nunca inventar AMI/subnet."
  type = object({
    ami                         = string
    instance_type               = string
    subnet_id                   = string
    security_group_ids          = set(string)
    key_name                    = optional(string)
    associate_public_ip_address = bool
    source_dest_check           = bool
    ebs_optimized               = bool
    monitoring                  = bool
    tags                        = map(string)
    http_endpoint               = string
    http_tokens                 = string
    http_put_response_hop_limit = number
    http_protocol_ipv6          = string
    instance_metadata_tags      = string
  })
}
