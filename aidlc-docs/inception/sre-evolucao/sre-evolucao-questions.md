# Perguntas — evolução de SRE

Responda após cada `[Answer]:` com a letra. Cada pergunta traz uma sugestão e o motivo.

## Q1. Qual o "patamar" alvo deste incremento?
A) SRE contínuo ainda em laboratório (SLO, burn rate, incidentes), sem migrar infraestrutura
B) A + durabilidade e capacidade homologada (S0–S6)
C) Prontidão de produção completa, incluindo plataforma AWS gerenciada (S0–S7)
D) Apenas fechar pendências da PoC (S0)
E) Outro

Sugestão: **B** — entrega o salto de maturidade sem o custo recorrente de AWS gerenciada; S7 fica para depois.

[Answer]: B

## Q2. Valores iniciais de SLO
A) Usar os do plano (J1 99,9%/30d; J2 fast 95% < 2 min, standard 90% < 45 min)
B) Começar mais folgado (J1 99,5%) e apertar após medir
C) Definir só após S6 (capacidade)
D) Outro

Sugestão: **B** — sem histórico contínuo, 99,9% tende a esgotar o budget por ruído de laboratório.

[Answer]: B

## Q3. Destino das notificações de incidente
A) Consumidor SQS que grava incidente local/arquivo + e-mail
B) PagerDuty/Opsgenie
C) Jira Service Management
D) Outro

Sugestão: **A** — fecha o ciclo sem nova ferramenta paga; troca de destino fica atrás de uma interface.

[Answer]: A

## Q4. Persistência para estado/outbox do worker (S5)
A) DynamoDB (já usado na Inbox)
B) PostgreSQL/Aurora
C) Manter em memória e só documentar o risco
D) Outro

Sugestão: **A** — reaproveita DynamoDB Local embarcado (sem Docker) e o mesmo modelo da Inbox.

[Answer]: A

## Q5. Onde rodar Prometheus/Grafana durante o incremento
A) Manter EC2 atual, ligando só nos ensaios
B) Amazon Managed Prometheus/Grafana
C) Processos locais na máquina
D) Outro

Sugestão: **A** — já provisionada e validada; custo controlado por stop/start.

[Answer]: A (assumida pela sugestão; confirmar)
