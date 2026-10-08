# **Finalidade**: Define os valores concretos das variáveis para o ambiente de desenvolvimento.

aws_region          = "sa-east-1"
environment         = "dev"
vpc_cidr            = "10.0.0.0/16"
vpc_azs             = ["sa-east-1a", "sa-east-1b", "sa-east-1c"]
vpc_public_subnets  = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
vpc_private_subnets = ["10.0.11.0/24", "10.0.12.0/24", "10.0.13.0/24"]
enable_nat_gateway  = true
single_nat_gateway  = true