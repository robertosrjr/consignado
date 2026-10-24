# Plano de Execução: Arquitetura, Envelope de Carga e Resiliência na Borda de Consignado

## 1. Contexto e Objetivos do Plano

Este **Plano de Execução** define a estratégia técnica, arquitetural e operacional para a Prova de Conceito (PoC) de alta vazão no mercado de Crédito Consignado (INSS, SIAPE/SouGov e Crédito do Trabalhador / eSocial / FGTS Digital).

### O Desafio de Negócio
No mercado de consignado, o evento mensal conhecido como **"virada de folha"** gera uma inundação imediata de tráfego. Em questão de segundos, a volumetria salta de **50 requisições por segundo (TPS)** para um pico de **10.000 TPS**, disparado principalmente por robôs e scripts automatizados de correspondentes bancários ("pastinhas") disputando a margem consignável dos clientes.

### O Objetivo Arquitetural
Construir uma **Borda de Absorção Absoluta** capaz de interceptar e registrar duravelmente até 10.000 TPS na nuvem com tempo de resposta $p95 < 100	ext{ ms}$, devolvendo a confirmação de recebimento `HTTP 202 Accepted` enquanto isola e protege o *Core Banking* legado, contendo seu consumo a estritamente **$\le 180	ext{ TPS}$**.

---

## 2. Modelagem do Envelope de Carga e Fator de Replay

### A Ilusão Matemática da Drenagem
Um cálculo ingênuo assume que 3.000.000 de propostas recebidas durante um pico de 5 minutos (10.000 TPS) processadas a 180 TPS levarão 4 horas e 38 minutos para drenar. Se a promessa comercial for esvaziar a fila em menos de 1 hora, essa matemática ingênua quebra a confiança do negócio.

### A Realidade da Trincheira: Fator de Replay (80/20)
Na prática da virada de folha, até **80% do tráfego das 10.000 TPS é composto por re-tentativas e duplicatas** disparadas pelos mesmos robôs de correspondentes após timeouts transitórios ou receio de perda de margem.

```
                  ┌──────────────────────────────────────────────────────────┐
                  │ TRÁFEGO TOTAL DE ENTRADA NA BORDA (10.000 TPS / 5 MIN)   │
                  │ Total de Chamadas: 3.000.000 requisições HTTP           │
                  └────────────────────────────┬─────────────────────────────┘
                                               │
                                               ▼
                  ┌──────────────────────────────────────────────────────────┐
                  │  FILTRAGEM ATÔMICA POR BUSINESS FINGERPRINT (DynamoDB)   │
                  └──────────────┬────────────────────────────┬──────────────┘
                                 │                            │
             ┌───────────────────┴───────────────┐        ┌───┴──────────────────────────────┐
             │ 80% REPLAYS / DUPLICATAS          │        │ 20% PROPOSTAS CANÔNICAS NOVAS    │
             │ (2.400.000 requisições)           │        │ (600.000 propostas)              │
             │ Resposta: 202 DUPLICATE_ACCEPTED  │        │ Resposta: 202 RECEIVED           │
             │ Custo de Fila: ZERO (Descarte)    │        │ Enfileiramento no SQS            │
             └───────────────────────────────────┘        └──────────────┬───────────────────┘
                                                                         │
                                                                         ▼
                                                          ┌──────────────────────────────────┐
                                                          │ DRENAGEM NO LEGADO (180 TPS)     │
                                                          │ Tempo Real: ~55 minutos          │
                                                          └──────────────────────────────────┘
```

Ao aplicar a deduplicação atômica na entrada via **`BusinessFingerprint` (HMAC-SHA256)**, o volume real de propostas novas cai de 3.000.000 para **600.000**, reduzindo o tempo de drenagem real no legado de 4h38m para **aproximadamente 55 minutos**.

---

## 3. Arquitetura Transacional e Idempotência na Borda

To solucionar a divergência entre a gravação no banco NoSQL e a mensageria, o plano especifica o tratamento de estados e a prevenção de mensagens órfãs:

```
                  ┌──────────────┐
                  │ Ingestion    │
                  │ API (Java 21)│
                  └──────┬───────┘
                         │
                         ▼
        ┌──────────────────────────────────┐
        │ DynamoDB: DurableInbox           │
        │ PK = HASH#<fingerprint>#PARTITION│
        │ condition: attribute_not_exists  │
        └────────────────┬─────────────────┘
                         │ (Gravação OK: status=RECEIVED)
                         ▼
        ┌──────────────────────────────────┐      (Se falhar link/timeout)     ┌──────────────────────────────────┐
        │ DynamoDB Streams / Dispatcher    │──────────────────────────────────>│ Sweeper Job (Reconciliação)      │
        └────────────────┬─────────────────┘                                   │ Varre RECEIVED > 30s sem SQS     │
                         │                                                     └────────────────┬─────────────────┘
                         ▼                                                                      │
        ┌──────────────────────────────────┐                                                    │
        │ Amazon SQS (Fast / Standard)     │<───────────────────────────────────────────────────┘
        └────────────────┬─────────────────┘
                         │
                         ▼
        ┌──────────────────────────────────┐
        │ Worker Fleet (Virtual Threads)   │
        └────────────────┬─────────────────┘
                         │
                         ▼
        ┌──────────────────────────────────┐
        │ Core Banking Legado (<= 180 TPS) │
        └──────────────────────────────────┘
```

### Resolução de Timeouts Ambíguos (HTTP 503 x Persistência)
Quando uma requisição sofre um timeout de rede de 500 ms (`ApiCallTimeoutException`) e a API responde `HTTP 503`, mas a escrita é concluída no DynamoDB 20 ms depois:
1. O robô re-envia o payload mantendo a mesma chave ou os mesmos campos canônicos.
2. A Ingestion API re-calcula o hash do `BusinessFingerprint`.
3. A escrita condicional no DynamoDB aciona `ConditionalCheckFailedException`.
4. A API reconhece o registro existente em estado `RECEIVED` ou `DISPATCHED` e retorna `HTTP 202 Accepted` com o cabeçalho `X-Idempotency-Status: DUPLICATE_ACCEPTED`, recuperando a operação sem gerar nova cobrança ou contrato duplicado.

---

## 4. Governança de Filas e QoS Anti-Inanição (Dynamic Score QoS)

A capacidade de 180 TPS do legado é dividida entre duas faixas operacionais no SQS:
* **Fast Lane (70% - 126 TPS)**: Destinada a propostas de alto VGV, parceiros VIPs ou operações de portabilidade com risco imediato de retenção.
* **Standard Lane (30% - 54 TPS)**: Destinada ao fluxo regular de propostas.

### Algoritmo Anti-Starvation (Aging Factor)
Para evitar que a Standard Lane sofre inanição permanente (*Queue Starvation*) durante rajadas prolongadas de propostas VIP, o Dispatcher injeta um fator de envelhecimento temporal na pontuação da proposta:

$$	ext{PriorityScore} = 	ext{BaseScore} + (lpha \cdot \Delta t)$$

Onde $\Delta t$ representa o tempo de retenção na fila em segundos. À medida que uma proposta comum permanece na fila, seu score aumenta progressivamente até atingir o patamar da Fast Lane, garantindo equidade (*Weighted Fair Queueing*).

---

## 5. Mapeamento SRE: SLIs, SLOs e Alertas Pragmáticos

### Jornada 1: Borda de Ingestão (Síncrona)
* **SLI 1.1 (Disponibilidade de Aceite)**: $\ge 99{,}9\%$ de respostas `HTTP 202` sobre o total de chamadas elegíveis no pico.
* **SLI 1.2 (Latência de Borda)**: $p95 < 100	ext{ ms}$ e $p99 < 200	ext{ ms}$.
* **SLI 1.3 (Retenção de Duplicatas)**: 100% de deduplicação sem propagação de mensagens repetidas para o SQS.

### Jornada 2: Processamento e Legado (Assíncrona)
* **SLI 2.1 (Effective Drain Time)**: Fast Lane com 95% concluído em $< 2	ext{ min}$; Standard Lane com 90% concluído em $< 45	ext{ min}$.
* **SLI 2.2 (Contenção do Legado)**: 100% do tempo mantido a $\le 180	ext{ TPS}$.
* **SLI 2.3 (Frescor da Fila)**: Idade da mensagem mais antiga (*Queue Lag*) $< 60	ext{ min}$.

### Alertas de Sobrevivência (Prevenção de Alert Fatigue)
Para evitar falsos positivos de madrugada provocados por testes de fumaça de QA ou variações insignificantes de tráfego, os alertas estáticos no Alertmanager exigem um **piso de volume mínimo**:
* *Exemplo (`ConsignadoIngestionErrors`)*: Dispara severidade `critical` apenas se a taxa de erro 5xx for $> 5\%$ **e** houver pelo menos **100 requisições HTTP na janela de 5 minutos**.

---

## 6. Sprints de Execução e Definition of Done (DoD)

### Cronograma de Sprints
* **Sprint 1 (Fundação & Mock Legado)**: Construção do mock de 180 TPS com simulação de latência e contadores do Micrometer.
* **Sprint 2 (Borda de Absorção & Idempotência)**: Ingestion API em Java 21 / Virtual Threads, DynamoDB `DurableInbox` e escrita condicional por `BusinessFingerprint`.
* **Sprint 3 (Dispatcher & Filas SQS)**: Filas `sqs-fast-lane`, `sqs-standard-lane` e `sqs-dlq`; Lambda Dispatcher com algoritmo de aging.
* **Sprint 4 (Worker Fleet & Capacity Budget)**: Consumidores ECS Fargate com Resilience4j (Rate Limiter e Circuit Breaker) contendo a saída em 180 TPS.
* **Sprint 5 (Telemetria & Painéis de Negócio)**: Dashboard Grafana com VGV Recebido vs. VGV Confirmado e alertas Alertmanager.

### Definition of Done (DoD)
1. **Injeção de Carga**: Carga de 10.000 TPS mantida por 5 minutos no k6.
2. **Disponibilidade na Borda**: $> 99{,}9\%$ das respostas em `HTTP 202` com $p95 < 100	ext{ ms}$.
3. **Contenção no Legado**: Zero ocorrências de tráfego $> 180	ext{ TPS}$ no mock do Core Banking em qualquer janela de 1 segundo.
4. **Deduplicação Atômica**: Carga concorrente de 50.000 replays produz exatamente 0 registros duplicados no banco final.
5. **Drenagem Sustentável**: Esvaziamento completo do backlog em ~55 minutos mantendo a integridade transacional.
