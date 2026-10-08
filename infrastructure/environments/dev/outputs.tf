output "vpc_id" {
  description = "ID da VPC gerada pelo módulo"
  value       = module.vpc.vpc_id
}

output "public_subnets" {
  description = "Lista com os IDs das sub-redes públicas criadas"
  value       = module.vpc.public_subnets
}

output "private_subnets" {
  description = "Lista com os IDs das sub-redes privadas criadas"
  value       = module.vpc.private_subnets
}

output "igw_id" { 
    description = "ID do Internet Gateway criado pela VPC"
     value = module.vpc.igw_id 
}