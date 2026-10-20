# SRE no Consignado: O que os Dashboards Verdes Escondem no Pico de Tráfego

*Gambiarras de PoC, falsos alertas, métricas de negócio e as lições reais de operar uma plataforma de crédito de alta vazão.*

---

## 1. O Cenário: A "Corrida de Milissegundos" na Virada de Folha

No mercado de crédito consignado, o momento de liberação de margem — a famosa **"virada de folha"** — é o evento mais agressivo do mês. Em questão de minutos, o tráfego salta de pacatos 50 requisições por segundo para uma avalanche de **10.000 requisições por segundo**.

Nesse exato instante, o banco que registrar a intenção e averbar a proposta primeiro fica com o contrato; o segundo a chegar encontra a margem do cliente já bloqueada pela concorrência.

No painel do Grafana, a tentação é comemorar: a taxa de respostas `HTTP 202 Accepted` está alta, a latência de borda permanece em **p95 < 100 ms** e o indicador de tráfego brilha em verde. Mas, 20 minutos depois, a diretoria comercial avisa que as propostas não estão caindo no sistema do governo.

A telemetria tradicional nos prega peças cruéis: em sistemas distribuídos assíncronos, **responder `HTTP 202` significa apenas que a API recebeu a proposta na borda; não garante que o valor financeiro foi processado ou averbado no legado**.

---

## 2. A Bússola de Negócio: VGV Recebido vs. VGV Confirmado

Em arquiteturas que protegem o *Core Banking* legado (limitando a vazão do legado a estritamente **180 TPS** enquanto a borda absorve milhares de chamadas), olhar apenas para métricas de infraestrutura (CPU, RAM, Uptime) é atuar no escuro.

Para conectar a engenharia de confiabilidade ao resultado financeiro, é indispensável monitorar o **VGV (Valor Global de Vendas)**:

* **VGV Recebido (`business_vgv_received_brl_sum`)**: Soma dos valores das propostas novas aceitas duravelmente na borda (`RECEIVED`).
* **VGV Confirmado (`business_vgv_brl_sum`)**: Soma dos valores das propostas efetivamente averbadas e liquidadas no legado (`CONFIRMED`).

> **O Diagnóstico do Arquiteto**: Se a curva do **VGV Recebido** sobe vertiginosamente enquanto o **VGV Confirmado** permanece estagnado, o sistema está sofrendo um **represamento oculto na fila**. O dashboard de TI está verde, mas a empresa está perdendo milhões de reais em margem para a concorrência.

---

## 3. As Duas Jornadas dos Níveis de Serviço (SLIs e SLOs)

Como os sistemas possuem desacoplamento temporal, o plano de resiliência exige a **separação estrita entre a jornada de ingestão síncrona e a jornada de processamento assíncrono**:

```text
                  ┌──────────────────────────────────────────────────────────┐
                  │ JORNADA 1: Borda de Ingestão (Síncrona / Absorção)       │
                  │ SLI 1.1: Disponibilidade (HTTP 202 / Total Elegível)     │
                  │ SLI 1.2: Latência p95 < 100 ms / p99 < 200 ms            │
                  │ SLI 1.3: Deduplicação em < 15 ms (BusinessFingerprint)   │
                  └────────────────────────────┬─────────────────────────────┘
                                               │
                                               ▼
                  ┌──────────────────────────────────────────────────────────┐
                  │ JORNADA 2: Processamento & Legado (Assíncrona / Outbox)  │
                  │ SLI 2.1: Effective Drain Time (SLA Preditivo)            │
                  │ SLI 2.2: Contenção de Saturação (≤ 180 TPS no Legado)    │
                  │ SLI 2.3: Queue Lag / Idade da Mensagem Mais Antiga       │
                  └────────────────────────────┴─────────────────────────────┘
```

1. **Jornada 1 — Borda de Ingestão**:
   * **SLI 1.1 (Taxa de Aceite Durável)**: >= 99.9% de retornos `HTTP 202 Accepted` durante o pico.
   * **SLI 1.2 (Latência de Borda)**: p95 < 100 ms e p99 < 200 ms.
   * **SLI 1.3 (Deduplicação Atômica)**: **100%** de retenção na entrada por **`BusinessFingerprint`** via HMAC-SHA256 e *Write Sharding* no DynamoDB.

2. **Jornada 2 — Processamento Assíncrono & Legado**:
   * **SLI 2.1 (Effective Drain Time)**: Finalização por faixas de prioridade (**Fast Lane < 2 min** vs. **Standard Lane < 45 min** com algoritmo anti-starvation).
   * **SLI 2.2 (Contenção do Legado)**: **100% do tempo mantido a <= 180 TPS** no *Core Banking*.
   * **SLI 2.3 (Frescor da Fila)**: **Nenhuma mensagem retida por mais de 60 minutos** sem encaminhamento automático para Dead Letter Queue (DLQ).

---

## 4. Smart Idempotency & Ambiguidade de Timeout

Um problema clássico na produção é o **timeout com gravação fantasma**: quando a camada NoSQL sofre uma lentidão transitória e estoura o timeout de borda (500 ms), a API responde `HTTP 503 Service Unavailable`. Porém, escassos 20 milissegundos depois, a gravação é concluída com sucesso no banco de dados.

Para evitar que *retries* descontrolados de robôs gerem contratos duplicados, a arquitetura diferencia dois conceitos:

* **Chave Técnica (`Idempotency-Key`)**: Trata repetições de transporte quando o cliente preserva a mesma chave no cabeçalho.
* **Equivalência de Domínio (`Business Fingerprint`)**: Calcula um hash **`HMAC-SHA256`** sobre os campos canônicos imutáveis da proposta (CPF sanitizado, Convênio, Valor em `BigDecimal` escala 2, Parcelas e Data UTC).

Se o parceiro reenviar a chamada após um erro `503`, a gravação atômica (`attribute_not_exists`) detecta o registro existente e retorna `HTTP 202` com o cabeçalho `X-Idempotency-Status: DUPLICATE_ACCEPTED`, recuperando a operação sem duplicar o contrato financeiro.

---

## 5. Alertas de Sobrevivência contra Fadiga do PagerDuty

Embora o modelo de **Multi-Window Multi-Burn-Rate Alerting** baseado no consumo do *Error Budget* seja a recomendação máxima dos manuais do Google SRE, em sistemas novos ou durante uma PoC de alta vazão, adotar **alertas estáticos pragmáticos** é o que salva a equipe no dia 1.

Para evitar a **fadiga de alertas (*alert fatigue*)**, as regras do Alertmanager exigem um **piso de tráfego mínimo** antes de disparar incidentes:

* **Exemplo**: A regra `ConsignadoIngestionErrors` exige uma taxa de erro 5xx superior a 5% **combinada com pelo menos 100 requisições HTTP na janela de 5 minutos**.

Isso impede que testes curtos de fumaça da equipe de QA ou falhas isoladas de 2 ou 3 requisições acordem o plantonista às 3h da manhã.

---

## 6. Governança e Privacidade (LGPD) em Telemetria

Sistemas financeiros exigem rigor na proteção de dados pessoais (PII):

1. **Banimento de PII em Rótulos (Labels)**: **Nunca utilize CPF, e-mail, tokens JWT ou chaves de proposta como labels do Prometheus**. Isso causa **explosão de cardinalidade**, derrubando o banco de séries temporais (*TSDB*) e violando os princípios de minimização da LGPD. As dimensões de métricas devem ser puramente categóricas (`service`, `env`, `outcome`, `convenio`, `portability`).
2. **Propagação de Contexto W3C (OpenTelemetry)**: O rastreamento distribuído real exige a passagem dos cabeçalhos W3C **`traceparent`** e **`tracestate`** nas mensagens do SQS via OTLP, em vez de depender de UUIDs aleatórios isolados no MDC.

---

## 7. Fechamento e Isenção Pragmática

Toda a arquitetura, volumetria e métricas analisadas neste trabalho pertencem a um **cenário simulado de Prova de Conceito (PoC)**. A intenção jamais foi ditar dogmas universais de SRE, mas sim **simular o estresse extremo da virada de folha**, testar limites de engenharia e demonstrar como alinhar observabilidade de TI a resultados de negócio.

A boa engenharia de software não busca atingir uma maturidade teórica perfeita, mas sim entender os *trade-offs*, proteger o *Core Banking* e garantir a entrega contínua de valor com transparência técnica.
