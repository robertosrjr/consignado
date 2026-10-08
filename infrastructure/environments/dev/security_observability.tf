# ------------------------------------------------------------------------------
# 4. Security Group para a Camada de Observabilidade (Prometheus/Grafana/Stack)
# ------------------------------------------------------------------------------
resource "aws_security_group" "observability" {
  name        = "${var.environment}-sg-observability-sg"
  description = "Security Group para a stack de observabilidade (Grafana, Prometheus, Alertmanager)"
  vpc_id      = module.vpc.vpc_id

  tags = {
    Name        = "${var.environment}-observability-sg"
    Environment = var.environment
  }
}

# Regra: SSH (Putty) - Porta 22
resource "aws_vpc_security_group_ingress_rule" "obs_ssh" {
  security_group_id = aws_security_group.observability.id
  cidr_ipv4         = "${chomp(data.http.myip.response_body)}/32"
  from_port         = 22
  to_port           = 22
  ip_protocol       = "tcp"
  description       = "Putty - Acesso SSH restrito ao IP do administrador"
}

# Regra: Grafana - Porta 3000
resource "aws_vpc_security_group_ingress_rule" "obs_grafana" {
  security_group_id = aws_security_group.observability.id
  cidr_ipv4         = "${chomp(data.http.myip.response_body)}/32"
  from_port         = 3000
  to_port           = 3000
  ip_protocol       = "tcp"
  description       = "Grafana - Interface web de dashboards"
}

# Regra: Prometheus - Porta 9090
resource "aws_vpc_security_group_ingress_rule" "obs_prometheus" {
  security_group_id = aws_security_group.observability.id
  cidr_ipv4         = "${chomp(data.http.myip.response_body)}/32"
  from_port         = 9090
  to_port           = 9090
  ip_protocol       = "tcp"
  description       = "Prometheus - Servidor de metricas"
}

# Regra: Pushgateway - Porta 9091
resource "aws_vpc_security_group_ingress_rule" "obs_pushgateway" {
  security_group_id = aws_security_group.observability.id
  cidr_ipv4         = "${chomp(data.http.myip.response_body)}/32"
  from_port         = 9091
  to_port           = 9091
  ip_protocol       = "tcp"
  description       = "Pushgateway - Ingestao de metricas ephemeras"
}

# Regra: Alertmanager - Porta 9093
resource "aws_vpc_security_group_ingress_rule" "obs_alertmanager" {
  security_group_id = aws_security_group.observability.id
  cidr_ipv4         = "${chomp(data.http.myip.response_body)}/32"
  from_port         = 9093
  to_port           = 9093
  ip_protocol       = "tcp"
  description       = "Alertmanager - Gerenciamento de alertas do Prometheus"
}

resource "aws_vpc_security_group_egress_rule" "obs_egress_all" {
  security_group_id = aws_security_group.observability.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
  description       = "Permite trafego de saida geral"
}