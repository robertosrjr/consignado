# Requirement Verification Questions

Os documentos em `sdd/` sao ricos, mas contem divergencias entre si e deixam decisoes de escopo em aberto. Cada pergunta traz uma **Sugestao** da IA com a justificativa. Para aceitar, copie a letra para `[Answer]:`; para divergir, escolha outra letra ou descreva em "Other".

**Decisao ja fixada pelo usuario:** Java 25 (LTS) em todos os servicos (substitui o Java 21 dos documentos).

---

## Question 1: Escopo de entrega da PoC
O SDD descreve 10.000 TPS na AWS (WAF, ALB, ECS Fargate, DynamoDB, Lambda, SQS, Aurora). Qual e o escopo desta entrega?

A) Codigo + Terraform completos para a AWS, com teste de carga real (k6) na AWS
B) Codigo + Terraform completos, mas validacao local/reduzida (LocalStack + Docker Compose); Terraform apenas validado (plan), sem deploy
C) Somente codigo e execucao local (Docker Compose + LocalStack), sem Terraform nesta fase
D) Other (please describe after [Answer]: tag below)

**Sugestao: B.** Entrega o IaC e todo o codigo sem custo nem risco de deploy; 10.000 TPS reais na AWS exigem conta, quotas e orcamento. O k6 roda em escala reduzida localmente, e o script ja fica pronto para a escala cheia.

[Answer]: B

## Question 2: Capacidade do legado - divergencia entre documentos
O SDD fala em limite seguro de 180 TPS; a Especificacao AI-DLC cita capacidade nominal de 200 TPS com alvo operacional de 180 TPS. Como tratar?

A) 180 TPS e o teto rigido (200 e apenas capacidade nominal informativa) - 126 TPS Fast / 54 TPS Standard
B) 200 TPS como teto, 180 como alvo configuravel
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** O DoD dos tres documentos exige legado <= 180 TPS; o valor fica configuravel, mas o default e 180 (126/54).

[Answer]: A

## Question 3: Modelo de dados - divergencias entre documentos
Os documentos divergem: `convenioType` (INSS, ESOCIAL_CLT, SIAPE) vs `agreementType` (INSS, ESOCIAL, ECONSIG, OPEN_FINANCE); `isPriority` (boolean) vs `priorityScore` (numerico); `customerCpf` vs `customer_id`; payload `status: ACCEPTED` vs `RECEIVED`/`DUPLICATE_ACCEPTED`. Qual contrato adotar?

A) Contrato do SDD principal (`sdd-credito-consignado-poc.md`) como fonte de verdade, ajustando apenas o necessario
B) Contrato da Especificacao AI-DLC (`agreementType`, `priorityScore`, `RECEIVED`/`DUPLICATE_ACCEPTED`, `tracingId`)
C) Hibrido: enum de convenio do SDD + `priorityScore` numerico + respostas `RECEIVED`/`DUPLICATE_ACCEPTED` (a IA consolida e documenta)
D) Other (please describe after [Answer]: tag below)

**Sugestao: C.** Pega o melhor de cada um: enum de convenio do SDD (`INSS`, `ESOCIAL_CLT`, `SIAPE_PUBLICO`), `priorityScore` (mais expressivo que boolean) e respostas `RECEIVED`/`DUPLICATE_ACCEPTED`, que distinguem duplicidade para o parceiro. Consolido tudo em um OpenAPI unico.

[Answer]: C

## Question 4: CPF e dados pessoais (LGPD)
O payload contem CPF do cliente, guardado no DynamoDB e no Aurora. Como tratar PII?

A) CPF em claro apenas em repouso criptografado (KMS); logs/metricas/traces sempre mascarados ou sem PII
B) CPF armazenado como hash/token (nunca em claro), logs sem PII
C) Fora do escopo da PoC (dados sinteticos), mas manter logs sem PII
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** O worker precisa do CPF real para chamar o legado, entao hash nao serve no payload. Criptografia em repouso (KMS) + zero PII em logs, metricas e traces atende a skill de LGPD sem quebrar o fluxo.

[Answer]: A

## Question 5: Autenticacao JWT
A Ingestion API exige JWT Bearer. Como validar na PoC?

A) Validacao real de assinatura via JWKS/issuer configuravel (emissor mock no ambiente de teste)
B) Validacao de JWT com chave simetrica/estatica de teste (sem IdP externo)
C) Apenas checar presenca do header na PoC (validacao real fica como evolucao)
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Validacao real (assinatura, issuer, expiracao) com o Spring Security Resource Server; um emissor mock sobe no Docker Compose. Em producao basta trocar a URL do JWKS. Nao ha segredo estatico no codigo.

[Answer]:A

## Question 6: Rate limiting por tenant - quotas
O requisito pede cotas "dinamicas por tenant" e 429 na borda. Qual a fonte das cotas?

A) Configuracao estatica (application.yml/env) por tenant, com default; Resilience4j na aplicacao + WAF no Terraform
B) Cotas em tabela DynamoDB, lidas com cache, alteraveis em runtime
C) Somente WAF (rate-based rule por header X-Tenant-ID), sem limiter na aplicacao
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Simples e testavel localmente (WAF nao roda no LocalStack). O limiter na aplicacao e **local por instancia**; isso sera documentado, com o WAF como camada distribuida. Tabela dinamica fica como evolucao.

[Answer]:A

## Question 7: Estrutura de modulos e repositorio
Qual estrutura de codigo em `applications/`? (Java 25 ja fixado; a versao do Spring Boot sera uma compativel com Java 25, a confirmar.)

A) Multi-modulo Maven (parent + ingestion-api, dispatcher-lambda, worker, mock-legacy, shared) com Java 25 / Spring Boot compativel (3.5.x ou 4.x)
B) Projetos independentes (um pom por servico), sem parent comum
C) Gradle multi-projeto
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Um parent controla versoes e plugins (Spotless, JaCoCo, Enforcer com Java 25); modulos separados mantem fronteiras claras entre os servicos.

[Answer]:A

## Question 8: Arquitetura interna dos servicos
O projeto tem agentes/skills de Arquitetura Hexagonal e DDD (`architecture-auditor`). Aplicar nos servicos?

A) Sim - Hexagonal (ports/adapters) com camadas domain/application/infrastructure em todos os servicos Java
B) Hexagonal apenas em Ingestion API e Worker; Lambda Dispatcher e Mock Legacy simples
C) Estrutura em camadas simples (controller/service/repository)
D) Other (please describe after [Answer]: tag below)

**Sugestao: B.** Ingestion API e Worker concentram regras de negocio (idempotencia, QoS, outbox) e merecem hexagonal com ArchUnit. O Dispatcher e o Mock Legacy sao finos e/ou descartaveis; hexagonal neles seria cerimonia sem retorno (YAGNI).

[Answer]:B

## Question 9: Dispatcher - pontuacao de prioridade
O Dispatcher decide Fast vs Standard por convenio, portabilidade Open Finance, LTV e VIP. LTV, rating do parceiro e seguros acoplados nao constam no contrato de ingestao. Como tratar?

A) Regra simples e deterministica: prioridade = portabilidade (`isPortability`) OU parceiro VIP (lista configuravel) OU valor acima de um limiar configuravel
B) Score ponderado configuravel (convenio, portabilidade, valor, rating do parceiro simulado)
C) Somente `isPortability` define Fast Lane
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Usa so dados que existem no contrato, e o valor do contrato e proxy razoavel de LTV. Deterministica e facil de testar (ideal para property-based tests); o resultado ainda vira `priorityScore`, mantendo a porta aberta para o score ponderado.

[Answer]:A

## Question 10: Worker - Aurora e Outbox
Qual a semantica esperada entre a chamada ao legado e a gravacao no Aurora?

A) Aurora grava `proposal` + `proposal_event` (outbox) na mesma transacao apos sucesso no legado; idempotencia via `idempotency_key UNIQUE`; falhas -> retry SQS e DLQ apos 3 tentativas
B) Gravar `proposal` com status PENDING antes de chamar o legado e atualizar apos; outbox em cada transicao de estado
C) Other (please describe after [Answer]: tag below)

**Sugestao: B.** Se o worker cair depois do legado e antes do Aurora, a opcao A reenvia ao legado (risco de averbacao dupla: o pior cenario do negocio). Gravar PENDING primeiro, com `idempotency_key UNIQUE`, e enviar ao legado com a mesma chave de idempotencia fecha esse buraco. Sem retry cego em escrita financeira, conforme a skill de resiliencia.

[Answer]:B

## Question 11: Publicacao do Outbox
O SDD define a tabela `proposal_event` mas nao quem publica os eventos de integracao. Incluir publicador?

A) Nao - somente registrar na tabela (historico auditavel) nesta PoC
B) Sim - relay simples (polling) que publica eventos pendentes (ex.: SNS/log)
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** O SDD so exige historico auditavel e rastreabilidade; nenhum criterio de aceite depende de publicacao. O relay fica como evolucao.

[Answer]:A

## Question 12: Observabilidade
O blueprint cita Prometheus, Pushgateway, Alertmanager, Grafana e OpenTelemetry/Jaeger. Qual o escopo?

A) Micrometer + Prometheus + Grafana (3 paineis do SDD) + OpenTelemetry/Jaeger com `traceparent` ponta a ponta; sem Alertmanager
B) Escopo completo do blueprint (inclui Pushgateway e Alertmanager com alertas)
C) Minimo: Micrometer + Prometheus + Grafana; tracing fora de escopo
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Cobre os 3 paineis e as metricas do DoD, mais o rastreio ponta a ponta (borda -> legado). Pushgateway e desnecessario (servicos de longa duracao); Alertmanager fica fora por nao haver criterio de aceite.

[Answer]:A

## Question 13: Simulador de convenios
O Sprint 1 cita mocks de Dataprev, eSocial e eConsig/SouGov. Eles fazem parte do escopo?

A) Sim, como modulo mock simples (validacao de margem/averbacao com latencia simulada) chamado pelo Mock Legacy
B) Nao - apenas o Mock Legacy com limite de 180 TPS
C) Other (please describe after [Answer]: tag below)

**Sugestao: B.** Nenhum criterio do DoD depende deles. O Mock Legacy ja prova a protecao dos 180 TPS; simuladores de convenio entram numa fase posterior.

[Answer]:B

## Question 14: Testes de carga e criterios de aceite
O SDD pede 50.000 requisicoes duplicadas; a Especificacao AI-DLC pede 500. Qual adotar no teste de idempotencia?

A) 50.000 concorrentes (DoD do SDD); 500 como smoke test rapido
B) 500 apenas
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** 50.000 e o DoD oficial; 500 serve de smoke rapido no pipeline e na execucao local.

[Answer]:A

## Question 15: Extensao Security Baseline
Should security extension rules be enforced for this project?

A) Yes - enforce all SECURITY rules as blocking constraints (recommended for production-grade applications)
B) No - skip all SECURITY rules (suitable for PoCs, prototypes, and experimental projects)
X) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Apesar de ser PoC, ela e apresentada a comite e lida com CPF e dados financeiros; o projeto ja tem skills de seguranca e LGPD. Custo adicional baixo se aplicado desde o inicio.

[Answer]:A

## Question 16: Extensao Property-Based Testing
Should property-based testing (PBT) rules be enforced for this project?

A) Yes - enforce all PBT rules as blocking constraints (recommended for projects with business logic, data transformations, serialization, or stateful components)
B) Partial - enforce PBT rules only for pure functions and serialization round-trips (suitable for projects with limited algorithmic complexity)
C) No - skip all PBT rules (suitable for simple CRUD applications, UI-only projects, or thin integration layers with no significant business logic)
X) Other (please describe after [Answer]: tag below)

**Sugestao: B.** Ha funcoes puras com boa propriedade a testar (calculo de prioridade, divisao de orcamento 70/30, round-trip de serializacao/compressao do payload), mas o resto e integracao.

[Answer]:B

## Question 17: Regiao AWS, ambiente e restricoes de custo
Ha restricoes de regiao, conta, orcamento ou naming que devam entrar nos requisitos?

A) Sem restricoes: usar `sa-east-1`, ambiente unico `poc`, prefixo `consignado-poc`
B) Usar `us-east-1` (menor custo/maior disponibilidade de servicos), ambiente unico `poc`
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Dados de clientes brasileiros: manter em `sa-east-1` evita questao de residencia de dados (LGPD) e a latencia fica menor. Como o deploy nao faz parte do escopo sugerido (Q1-B), o custo regional nao pesa.

[Answer]:A


-----------------------------------

OBS:
1) Nesse primeiro momento não vamos ter o banco de dados, vamos fazer em memória a parte de persistencia, após finalizar colocamos o banco.mas o MVP-1 será testado localmente devido aos custos.

