#**Finalidade**: Configura o provedor AWS, a região de implantação e as tags padrão do ambiente.
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Environment = var.environment
      ManagedBy   = "Terraform"
      Project     = "Infra-AWS"
    }
  }
}