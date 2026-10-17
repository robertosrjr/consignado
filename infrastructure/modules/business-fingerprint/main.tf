terraform {
  required_version = ">= 1.7, < 2.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 6.28, < 7.0"
    }
  }
}

variable "table_name" {
  type    = string
  default = "ProposalDeduplication"
}

variable "inbox_table_arn" {
  type = string
}

variable "ingestion_role_name" {
  type = string
}

variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_dynamodb_table" "deduplication" {
  name                        = var.table_name
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "PK"
  range_key                   = "SK"
  deletion_protection_enabled = true
  attribute {
    name = "PK"
    type = "S"
  }
  attribute {
    name = "SK"
    type = "S"
  }
  ttl {
    attribute_name = "ttl"
    enabled        = true
  }
  point_in_time_recovery {
    enabled = true
  }
  server_side_encryption {
    enabled = true
  }
  lifecycle {
    prevent_destroy = true
  }
  tags = var.tags
}

resource "aws_kms_key" "fingerprint" {
  description              = "Consignado Business Fingerprint v2 HMAC (not payload encryption)"
  customer_master_key_spec = "HMAC_256"
  key_usage                = "GENERATE_VERIFY_MAC"
  deletion_window_in_days  = 30
  lifecycle {
    prevent_destroy = true
  }
  tags = var.tags
}

resource "aws_iam_role_policy" "ingestion" {
  name = "consignado-business-fingerprint"
  role = var.ingestion_role_name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:PutItem"]
        Resource = [var.inbox_table_arn, aws_dynamodb_table.deduplication.arn]
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:ConditionCheckItem"]
        Resource = aws_dynamodb_table.deduplication.arn
      },
      {
        Effect   = "Allow"
        Action   = ["kms:GenerateMac"]
        Resource = aws_kms_key.fingerprint.arn
      }
    ]
  })
}

output "fingerprint_table_name" {
  value = aws_dynamodb_table.deduplication.name
}

output "fingerprint_kms_key_arn" {
  value = aws_kms_key.fingerprint.arn
}
