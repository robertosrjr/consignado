# Evolução do Plano: Diretrizes e Roadmap para Transição de PoC para Produção

## 1. O Choque de Realidade: PoC vs. Produção Real

A Prova de Conceito (PoC) cumpriu seu papel fundamental: demonstrou a viabilidade do desacoplamento temporal, provou a eficácia do algoritmo de `BusinessFingerprint` na retenção de duplicatas e conectou a telemetria ao resultado financeiro por meio da comparação entre **VGV Recebido** e **VGV Confirmado**.

No entanto, em um ambiente de produção bancária de alta crítica, a aplicação não enfrenta apenas scripts previsíveis do k6 contra mocks locais. Ela enfrenta a **"lama da guerra" operacional**:
* Instabilidades recorrentes nos órgãos pagadores e registradores (**Dataprev, SouGov/eConsig, eSocial, Prodesp/SCC, CIP**).
* Mudanças não homologadas em contratos de API sem retrocompatibilidade.
* **Retaliação de taxa automatizada** pelos grandes bancos credores durante a janela de liquidação da portabilidade.
* Divergências entre o saldo devedor informado no Open Finance e o valor exato da TED de liquidação no DDC.

---

## 2. As 6 Eliminações Obrigatórias para Produção

Para elevar a arquitetura atual a um padrão de produção resiliente e auditável, seis limitações atuais devem ser eliminadas:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            ROADMAP DE ELIMINAÇÃO DE PONTOS DE RUPTURA                            │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘

   1. SPOF DE INFRAESTRUTURA          2. TRACING "FANTASMA"             3. ABISMO TRANSACIONAL
   ┌─────────────────────────┐        ┌─────────────────────────┐         ┌─────────────────────────┐
   │ DE: EC2 Única / Emulador│        │ DE: UUIDs no MDC        │         │ DE: Gravação síncrona   │
   │ PARA: ECS Fargate Multi-│        │ PARA: W3C Trace Context │         │ PARA: Transactional     │
   │ AZ + DynamoDB / SQS AWS │        │ (traceparent) no SQS    │         │ Outbox / Sweeper Job    │
   └─────────────────────────┘        └─────────────────────────┘         └─────────────────────────┘

   4. ANTI-PADRÃO PUSHGATEWAY         5. STARVATION DE FILA             6. ALERTAS ESTÁTICOS
   ┌─────────────────────────┐        ┌─────────────────────────┐         ┌─────────────────────────┐
   │ DE: Pushgateway local   │        │ DE: Roteamento estático │         │ DE: 7 Regras estáticas  │
   │ PARA: Scraping direto + │        │ PARA: Dynamic Aging     │         │ PARA: Multi-Window      │
   │ Queue Lag & DLQ Alert   │        │ Score (alpha * Delta t) │         │ Burn-Rate Alerting      │
   └─────────────────────────┘        └─────────────────────────┘         └─────────────────────────┘
```

1. **Eliminação do Ponto Único de Falha (SPOF)**:
   * *Ações*: Migrar todo o provisionamento via Terraform para **AWS ECS Fargate Multi-AZ**, **Amazon DynamoDB** com *Partition Salting* (`attribute_not_exists`), **Amazon SQS** gerenciado e **Amazon Aurora PostgreSQL**.
2. **Instalação do Tracing Distribuído W3C Real**:
   * *Ações*: Substituir o gerador de UUIDs aleatórios no MDC pela propagação nativa do **W3C Trace Context** (`traceparent` e `tracestate`) em todas as mensagens do SQS e chamadas HTTP via OpenTelemetry OTLP.
3. **Fechamento do Abismo Transacional (Outbox Pattern)**:
   * *Ações*: Eliminar a dependência do envio síncrono para o SQS após a escrita NoSQL. Implementar o padrão **Transactional Outbox** ou desacoplar via **DynamoDB Streams + Lambda Dispatcher**, respaldado por um **Sweeper Job reativo** que varre registros `RECEIVED` órfãos a cada 30 segundos.
4. **Substituição do Pushgateway por Scraping Direto e Monitoramento de DLQ**:
   * *Ações*: Apagar a dependência do Pushgateway (anti-padrão para microsserviços permanentes) em favor de *scraping* direto do Prometheus via Service Discovery no ECS. Adicionar métricas de **Queue Lag (`Oldest Message Age`)** e monitoramento ativo de mensagens retidas em **Dead Letter Queue (DLQ)**.
5. **Incorporação do Algoritmo Anti-Starvation**:
   * *Ações*: Parametrizar o algoritmo de envelhecimento ($lpha \cdot \Delta t$) no `PriorityScorer` do Dispatcher para garantir que mensagens na Standard Lane tenham sua prioridade elevada gradualmente.
6. **Evolução para Multi-Window Multi-Burn-Rate Alerting**:
   * *Ações*: Formalizar os contratos de SLO e configurar alertas baseados no ritmo de consumo do *Error Budget* (janelas curtas de 1h combinadas com janelas longas de 6h).

---

## 3. Desafios de Negócio, Regulação e Estratégia Anti-Retenção

A operação em produção exige plena conformidade com o arcabouço normativo vigente:

### 1. Marco Legal e Portabilidade no Open Finance
* **Resolução CMN nº 4.292/2013 e Resolução Conjunta CMN/BCB nº 15/2025**: A portabilidade via Open Finance encurta o prazo de liquidação para **até 3 dias úteis**.
* **Estratégia Anti-Retenção**: Para combater a contra-oferta automatizada do banco credor original durante a janela do DDC, a esteira de produção deve aplicar:
  * **Lock-in Biométrico Facial Antecipado**: Coleta da biometria nos termos da **IN PRES/INSS nº 138/2022 (Art. 5º)** no primeiro minuto do consentimento.
  * **Estruturação da Oferta com "Troco" (Refinanciamento)**: Liberação de saldo líquido em dinheiro novo, criando barreira contra tentativas de retenção simples por redução marginal de taxa.

### 2. Normativas Federais e Transição de Margem
* **Medida Provisória nº 1.355/2026**: Parametrizar os motores de simulação para a transição gradual da margem consignável de **45% para 40%** a partir de 2027.
* **Portarias MTE nº 433/2025 e 240/2026**: Cumprimento dos requisitos de integração de folha com eSocial, SERPRO e Caixa Econômica Federal.
* **Autenticação Segura via ICP-Brasil**: Obrigatoriedade do uso de certificados digitais PJ no padrão ICP-Brasil para todas as conexões OAuth 2.0 com Dataprev, SouGov e Prodesp/SCC.

---

## 4. Matriz de Roadmap e Fases de Implementação (P0 a P6)

| Fase | Escopo Técnico / Funcional | Entregáveis Principais | Critério de Aceite / Gate |
| :--- | :--- | :--- | :--- |
| **P0** | **Contrato e Governança** | Alinhamento de SLOs, definição da política de Error Budget e especificação do envelope de carga. | Aprovação formal entre Arquitetura, Produto e Operações. |
| **P1** | **Infraestrutura Gerenciada (IaC)** | Terraform para ECS Fargate Multi-AZ, DynamoDB real, SQS e Aurora PostgreSQL. | Deploy automatizado via CI/CD com aprovação em testes de segurança. |
| **P2** | **Resiliência & Outbox** | Implementação do Transactional Outbox / Sweeper Job e idempotência por `BusinessFingerprint`. | Carga de falhas injetadas (chaos) comprovando zero mensagens órfãs. |
| **P3** | **Observabilidade & W3C Tracing** | Propagação W3C OTLP no SQS, scraping direto no Prometheus e dashboards Grafana. | Árvore de Spans completa no Jaeger e ausência de PII nos rótulos. |
| **P4** | **Integrações de Órgãos & Open Finance** | Módulos de conexão OAuth 2.0 / ICP-Brasil com Dataprev, eConsig, eSocial e Open Finance. | Teste de comunicação em sandbox oficial com retorno válido de DDC. |
| **P5** | **SRE Avançado & Alerting** | Multi-Window Multi-Burn-Rate Alerting e monitoramento de Queue Lag / DLQ. | promtool e amtool validados com notificações reais via Alertmanager/SNS. |
| **P6** | **Go-Live & Piloto B2B** | Onboarding do Cliente Âncora / Design Partner (SCD ou Promotora) no modelo CaaS. | Processamento de volume real com conciliação financeira de 100%. |

---

## 5. Conclusão e Diretriz Executiva

A evolução do plano de PoC para Produção transfere o foco da capacidade isolada de código para a **sustentabilidade operacional e governança transacional**. 

Ao eliminar os pontos únicos de falha, fechar o abismo entre NoSQL e mensageria e adotar uma estratégia inteligente de prevenção de retenção no Open Finance, a plataforma se consolida como um ativo de alta performance pronto para suportar as operações mais exigentes e rentáveis do mercado de Crédito Consignado.
