# A Verdade Nua e Crua: O Apagão das 00:01 no Dia da Virada

**Status:** Documento de Especificação de Cenário Real  
**Aplicação:** Validação de PoC de Crédito Consignado perante Comitê de Arquitetura / Master / C-Level  
**Contexto de Mercado:** Mercado de Crédito Consignado no Brasil (R$ 384B+ em INSS/Público e R$ 100B+ em Consignado CLT / Crédito do Trabalhador)  

---

## 1. A Anatomia do "Ataque" Doméstico no Mercado de R$ 484 Bilhões

No mercado financeiro brasileiro, o crédito consignado é a linha de crédito pessoal de menor risco e maior rentabilidade. Com uma carteira consolidada que supera **R$ 384 bilhões** no INSS e setor público, e mais de **R$ 100 bilhões** no consignado privado (Crédito do Trabalhador via eSocial e FGTS Digital), a disputa entre instituições tradicionais (**Banco do Brasil, Itaú, Caixa**) e plataformas digitais emergentes (**Parati CFI/MeuTudo, Facta, PicPay**) é decidida em milissegundos.

A cada "virada de folha" (liberação da margem consignável pelo INSS, eSocial/SERPRO ou governos estaduais/eConsig), bilhões de reais em margem ficam disponíveis instantaneamente. 

### O Comportamento Real dos Atores no Momento Zero:
1. **O Cliente Final (Ex: Dona Maria ou Trabalhador CLT):** Realiza a simulação na véspera. O cliente não tolera falhas: se o sistema do banco travar às 00:01, o correspondente bancário redireciona a proposta para a concorrência.
2. **O Correspondente Bancário ("Pastinha") e as Fintechs Parceiras:** Para garantir a comissão e assegurar a margem do cliente antes dos rivais, esses parceiros não operam manualmente. Eles utilizam **robôs e scripts automatizados (Python/Node.js)** que disparam a mesma proposta dezenas ou centenas de vezes por segundo contra a API do banco no exato milissegundo em que a janela do órgão público abre.
3. **Pico Extremo de Tráfego:** Às 00:01, o tráfego do banco sofre um salto violento de uma média normal de **50 TPS para um pico brutal de 10.000 TPS**.

```
[ Robôs de Correspondentes / Fintechs ] ──► (10.000 TPS às 00:01)
                                                 │
                                                 ▼
                             ┌───────────────────────────────────────┐
                             │ ❌ ARQUITETURA SÍNCRONA TRADICIONAL   │
                             │ Core Banking Legado (Target: 180 TPS) │
                             └───────────────────┬───────────────────┘
                                                 │
                                                 ▼
                             🔥 Exaustão do Pool de Conexões (DB Locks)
                             🔥 Timeouts em Cascata & Queda do Sistema
                             🔥 Sanções da Autorregulação Febraban
```

---

## 2. O Prejuízo Real: Incompetência Técnica e Impacto no Balanço

Quando essa onda de 10.000 TPS atinge o **Core Banking legado** (projetado com capacidade operacional limite de **180 a 200 TPS**):

* **Exaustão de Conexões:** O pool de conexões com o banco de dados relacional se esgota em menos de 3 segundos.
* **Downtime e Reunião de Crise:** O sistema entra em *timeout* em cascata e cai. Às 00:15 da manhã, a liderança de TI e Operações é convocada às pressas para reiniciar servidores, limpar filas corrompidas e desbloquear conexões de banco de dados.
* **Perda Irrecuperável de Originação:** Durante os 40 a 60 minutos em que o banco permanece fora do ar tentando se recuperar, competidores ágeis e sistemas resilientes absorvem a demanda e registram as intenções. O banco perde **dezenas de milhões de reais em originação** em um único evento.
* **Risco de Averbação Dupla e Multas da Febraban:** No caos dos retries automáticos gerados pelos robôs, requisições duplicadas que passam simultaneamente geram *race conditions*, resultando em **averbações duplicadas no INSS/Dataprev ou eSocial**. Além do litígio judicial, a instituição fica exposta às rígidas sanções da **Autorregulação do Consignado (Febraban/ABBC)**, que já acumula mais de 2.260 medidas administrativas aplicadas a bancos e correspondentes por práticas operacionais irregulares.

---

## 3. A Tese de Defesa da PoC para o Comitê Master / C-Level

A PoC não propõe a insanidade financeira de "expandir o Core Banking legado para processar 10.000 TPS de forma síncrona". Em vez disso, ela demonstra e valida a **Borda de Absorção Absoluta com Desacoplamento Temporal**:

```
[ 10.000 TPS na Borda ]
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. INGESTION FLEET (Java 21 / Virtual Threads / AWS ECS)        │
│    • Rate Limiting Multi-Tenant por parceiro no WAF             │
│    • Validação de Schema e Resposta HTTP 202 em < 100 ms        │
└─────────┬───────────────────────────────────────────────────────┘
          │ (Escrita Atômica: attribute_not_exists)
          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. DURABLE INBOX (Amazon DynamoDB)                              │
│    • Absorve 10k TPS | Descarta 499 duplicatas (FinOps)         │
└─────────┬───────────────────────────────────────────────────────┘
          │ (DynamoDB Streams ─► AWS Lambda Dispatcher)
          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. GOVERNANÇA DE QOS & PORTABILIDADE (Amazon SQS)               │
│    • Fast Lane (70% Budget = 126 TPS) - Alto LTV / Open Finance │
│    • Standard Lane (30% Budget = 54 TPS) - Demais Operações     │
└─────────┬───────────────────────────────────────────────────────┘
          │ (Workers em Java 21 com Resilience4j Rate Limiter)
          ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. MOCK LEGADO (Proteção Rígida em 180 TPS)                     │
│    • Ingestão cadenciada e segura                               │
│    • Persistência relacional atômica (Aurora Outbox)            │
└─────────────────────────────────────────────────────────────────┘
```

### Os 4 Pilares Inquestionáveis da Solução:

1. **Idempotência Radical & FinOps:**
   Se um robô disparar 500 vezes a mesma proposta no mesmo segundo, a regra de escrita condicional no DynamoDB (`attribute_not_exists(idempotencyKey)`) grava a primeira e **rejeita as outras 499 silenciosamente**. Zera o risco de averbação dupla, elimina o desperdício de nuvem e protege o banco contra punições da Febraban.
2. **Absorção Imediata na Borda (SLA < 100 ms):**
   A Ingestion API em **Java 21 (Virtual Threads)** grava a intenção e responde `HTTP 202 Accepted` em p95 < 100 ms. O correspondente acalma os robôs ao receber o protocolo oficial do banco.
3. **Garantia de Margem de Lucro via QoS (Quality of Service):**
   O *AWS Lambda Dispatcher* lê o DynamoDB Streams e classifica o backlog no SQS:
   * **Fast Lane (70% da capacidade do legado / 126 TPS):** Propostas com seguros acoplados, alto LTV ou solicitações de portabilidade via Open Finance (Resolução Conjunta CMN/BCB nº 15/2025).
   * **Standard Lane (30% da capacidade / 54 TPS):** Operações padrão.
4. **Proteção Física do Legado:**
   Workers em ECS Fargate consomem o SQS e alimentam o Core Banking estritamente em **180 TPS** via *Resilience4j Rate Limiter*. O legado trabalha a 100% de eficiência sem sofrer sobressaltos.

---

## 4. Evidências de Validação na PoC (Métricas no Grafana)

Para homologar a PoC perante a engenharia Master, o teste de carga (k6/JMeter) comprova 4 métricas ao vivo:

* **Taxa de Absorção de Borda (`ingestion.accepted.rate`):** Carga sustentada de **10.000 TPS** com **> 99,9% de HTTP 202 Accepted** e p95 < 100 ms.
* **Linha Prata de Proteção do Legado (`legacy.request.throughput`):** Linha reta horizontal travada perfeitamente em **180 TPS**.
* **Integridade de Idempotência:** Injeção de 50.000 requisições concorrentes com chaves duplicadas produzindo **exatamente 1 registro no PostgreSQL**.
* **Effective Drain Time (`QueueDepth / EffectiveRate`):** Dashboard exibindo em tempo real o tempo estimado para esvaziar o backlog acumulado após o pico.
