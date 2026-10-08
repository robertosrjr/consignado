# **Finalidade**: Declara todas as variáveis de entrada utilizadas pelo projeto com descrições e tipos.

variable "aws_region" {
  description = "Região da AWS onde os recursos serão criados"
  type        = string
  default     = "sa-east-1"
}

variable "environment" {
  description = "Nome do ambiente de implantação"
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "Bloco de IP CIDR principal da VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "vpc_azs" {
  description = "Lista de Zonas de Disponibilidade para distribuição das sub-redes"
  type        = list(string)
  default     = ["sa-east-1a", "sa-east-1b", "sa-east-1c"]
}

variable "vpc_public_subnets" {
  description = "Lista de blocos CIDR para as sub-redes públicas"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
}

variable "vpc_private_subnets" {
  description = "Lista de blocos CIDR para as sub-redes privadas"
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24", "10.0.13.0/24"]
}

variable "enable_nat_gateway" {
  description = "Habilita ou desabilita a criação de NAT Gateways"
  type        = bool
  default     = true
}

variable "single_nat_gateway" {
  description = "Usa 1 NAT Gateway compartilhado (economia em dev) ou 1 por AZ (produção)"
  type        = bool
  default     = true
}