# S6 — Ambiente local

Quatro processos Java: local-env (DynamoDB Local, ElasticMQ, JWT e dispatcher), ingestion-api, mock-legacy e worker. Persistência do worker selecionada em local-up.ps1; não usar SreCapture, pois esse modo exige repositório em memória.

Prometheus 3.5.0 como processo local, configuração prometheus.local.yml, coleta HTTP direta nas portas 8080, 8081 e 8083; armazenamento de evidência dentro de applications/.local. Regras são as mesmas da configuração existente. Sem recursos AWS, containers ou instalação de serviço do sistema.

O gerador só conhece URLs loopback. Dados sintéticos e tokens transitórios; relatório não inclui tokens, CPF, payload ou chaves individuais. Encerrar os processos iniciados para o ensaio ao terminar.
