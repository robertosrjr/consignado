# Infrastructure Design - U1 platform-foundation

Decisoes do plano (todas A): Q1 emuladores sem conta/token; Q2 `local-runner` para o Dispatcher; Q3 Compose por perfis; Q4 imagem base Eclipse Temurin 25 JRE.

Escopo: ambiente **local** e politica de imagens. Nao ha recurso AWS implantavel em U1; nao ha `shared-infrastructure.md` (a infraestrutura AWS compartilhada pertence a U6).

## 1. Mapeamento Componente Logico -> Infraestrutura

| Componente logico (design) | Local (Compose / testes) | AWS (U6, referencia) |
|---|---|---|
| DurableInbox + Streams | **DynamoDB Local** (imagem oficial da AWS) | Amazon DynamoDB (Streams, TTL, SSE-KMS) |
| Filas fast, standard, DLQ | **ElasticMQ** (API compativel com SQS; redrive por `maxReceiveCount`) | Amazon SQS |
| Cifra do payload (CPF) | **local-kms** (API compativel com KMS) | AWS KMS (CMK) |
| Estado relacional | **PostgreSQL** | Aurora PostgreSQL Serverless v2 |
| Emissor JWT | **mock OAuth2 server** | IdP corporativo (fora do escopo da PoC) |
| Metricas | **Prometheus** | A decidir em U6 (ex.: CloudWatch ou Prometheus gerenciado) |
| Dashboards | **Grafana** | A decidir em U6 |
| Tracing | **Jaeger** (OTLP) | A decidir em U6 (ex.: X-Ray/OTel collector) |
| Lambda Dispatcher | **`local-runner`** (U4, perfil local) | AWS Lambda + event source mapping |
| Borda (WAF, ALB) | nao emulados | AWS WAF v2 + ALB (somente Terraform validado) |

## 2. Servicos do Compose (perfil `infra`)

Tags e digests exatos sao fixados no Code Generation consultando os registries (nao ha `latest`).

| Servico | Imagem (origem) | Porta (loopback) | Limites | Dados | Notas |
|---|---|---|---|---|---|
| `dynamodb-local` | `amazon/dynamodb-local` | 127.0.0.1:8000 | 0.5 CPU / 512 MB | volume nomeado opcional (`-dbPath`) | `-sharedDb`; Streams habilitado por tabela |
| `elasticmq` | `softwaremill/elasticmq-native` | 127.0.0.1:9324 (API), 9325 (UI) | 0.25 CPU / 256 MB | em memoria | filas definidas em `elasticmq.conf` |
| `local-kms` | `nsmithuk/local-kms` | 127.0.0.1:8081 | 0.25 CPU / 128 MB | seed YAML | chave de teste do payload |
| `postgres` | `postgres` (linha suportada) | 127.0.0.1:5432 | 0.5 CPU / 512 MB | volume nomeado | credenciais via `.env` |
| `oauth2-mock` | `ghcr.io/navikt/mock-oauth2-server` | 127.0.0.1:8082 | 0.25 CPU / 256 MB | - | JWKS e emissao por tenant |
| `prometheus` | `prom/prometheus` | 127.0.0.1:9090 | 0.5 CPU / 256 MB | volume nomeado | scrape 5 s |
| `grafana` | `grafana/grafana` | 127.0.0.1:3000 | 0.5 CPU / 256 MB | provisionamento por arquivos | admin via `.env`, sem senha padrao |
| `jaeger` | `jaegertracing/jaeger` | 127.0.0.1:16686 (UI), 4317/4318 internos | 0.5 CPU / 256 MB | em memoria | OTLP |
| `bootstrap` (one-shot) | `amazon/aws-cli` | - | 0.25 CPU / 128 MB | - | cria tabela `DurableInbox` (Streams, TTL), valida filas e chave KMS; sai com 0 |

Total aproximado de memoria reservada: **~2,5 GB** (mais as aplicacoes no perfil `apps`).

### Provisionamento local
- `DurableInbox`: PK `idempotencyKey` (S), `StreamSpecification` `NEW_IMAGE`, TTL em `ttl`; criado pelo `bootstrap` (script versionado).
- Filas: `sqs-fast-lane-queue`, `sqs-standard-lane-queue`, `sqs-dlq` com redrive (`maxReceiveCount=3`) em `elasticmq.conf`.
- KMS: chave de teste e alias carregados do seed de `local-kms`.
- Endpoints sao configurados por variavel de ambiente (`AWS_ENDPOINT_URL_DYNAMODB`, `AWS_ENDPOINT_URL_SQS`, `AWS_ENDPOINT_URL_KMS`); nenhum endpoint hardcoded; em producao as variaveis ficam ausentes.
- Credenciais AWS locais: valores de teste (`test`/`test`) via `.env.example`, documentados como excecao de desenvolvimento local.

## 3. Perfis do Compose (Q3-A)

| Perfil | Conteudo | Uso |
|---|---|---|
| `infra` (padrao) | Todos os servicos da tabela acima | Desenvolvimento pela IDE e testes |
| `apps` | `mock-legacy`, `ingestion-api`, `worker`, `dispatcher-local-runner` (entram em U2 a U5) | Execucao completa em containers e k6 |

Comando de referencia: `docker compose --profile infra up -d` e `docker compose --profile infra --profile apps up -d`.

## 4. Fidelidade e Limitacoes dos Emuladores

| Emulador | Cobre | Diferencas conhecidas | Mitigacao |
|---|---|---|---|
| DynamoDB Local | PutItem condicional, TTL (config), Streams | Sem throttling real de capacidade; TTL pode nao expirar como na AWS; limites de throughput nao simulados | Idempotencia testada na semantica da condicao; capacidade validada so no Terraform/AWS (fora do escopo) |
| ElasticMQ | Long polling, atributos de mensagem, DLQ/redrive, visibility timeout | Sem FIFO avancado, sem criptografia SSE, entrega diferente sob carga | Somente filas standard sao usadas; criptografia e requisito do Terraform |
| local-kms | Encrypt, Decrypt, GenerateDataKey | Politicas de chave e IAM nao aplicadas | Politicas validadas em U6 |
| PostgreSQL | SQL, Flyway, transacoes | Aurora tem particularidades de failover | Fora do escopo da PoC |

Esses limites ja estavam cobertos pelo risco A4 dos requisitos; a diferenca e que agora nao ha LocalStack.

## 5. Politica de Imagem dos Servicos (Q4-A)

Aplicada nos Dockerfiles de U2, U3 e U5 (e na referencia Fargate de U6).

- Base: **Eclipse Temurin 25 JRE** com tag exata (e digest quando disponivel); multi-stage (build com JDK Maven, runtime com JRE).
- Usuario nao-root (UID fixo, ex.: 10001); sistema de arquivos raiz somente leitura, com `tmpfs` para `/tmp`.
- Sem `latest`; sem shell/ferramentas extras alem do necessario.
- JVM: `-XX:MaxRAMPercentage=75`, `-XX:+ExitOnOutOfMemoryError`, Virtual Threads por configuracao; GC e flags finais definidos nas unidades.
- Health: sem `HEALTHCHECK` no Dockerfile; probes pelo Actuator (`/actuator/health/liveness` e `/readiness`) via Compose/ECS.
- Imagem e SBOM: SBOM CycloneDX do build anexado ao artefato; scan de imagem recomendado no CI (documentado em Build and Test).
- Dispatcher: o artefato Lambda e um JAR/ZIP; o runtime (Java 25 gerenciado ou imagem de container) e decidido no Infrastructure Design de U4.

## 6. Seguranca da Infraestrutura Local

- Todas as portas publicadas em `127.0.0.1`; redes `data` e `observability` separadas.
- `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]` e `read_only: true` onde a imagem suportar (registrar excecoes por servico no Code Generation).
- Senhas e chaves de teste vem de `.env` (nao versionado); `.env.example` versionado com valores nao sensiveis.
- Grafana e PostgreSQL sem credenciais padrao.
- Nenhum recurso do Compose e referenciado pelo Terraform.

## 7. Requisitos de Maquina e Pre-requisitos

| Item | Requisito |
|---|---|
| Container runtime | Docker Desktop ou Rancher Desktop (pendente de instalacao, risco R1) |
| Memoria | >= 8 GB disponiveis para o runtime (infra ~2,5 GB + aplicacoes + k6) |
| Portas livres | 3000, 5432, 8000, 8081, 8082, 9090, 9324, 9325, 16686 |
| Verificacao | `docker info` e `docker compose version` antes de U2 |

## 8. Compliance - Security Baseline (Infrastructure Design de U1)

| Regra | Status | Observacao |
|---|---|---|
| SECURITY-01 | N/A (excecao local documentada) | Emuladores locais sem TLS/at-rest; requisito aplicado nos recursos AWS de U6 |
| SECURITY-02 | N/A em U1 | ALB/WAF sao de U6 |
| SECURITY-03 | Compliant | Logs de containers em stdout; formato definido no NFR Design |
| SECURITY-04 | N/A | Sem HTML |
| SECURITY-05 | N/A em U1 | Validacao nos endpoints em U2/U3 |
| SECURITY-06 | N/A em U1 | Sem IAM implantavel; credenciais de teste isoladas |
| SECURITY-07 | Compliant | Loopback, redes dedicadas, sem `0.0.0.0` |
| SECURITY-08 | N/A em U1 | Autorizacao nos endpoints (U3) |
| SECURITY-09 | Compliant | Sem credenciais padrao, nao-root, imagens com tag exata, runtimes suportados |
| SECURITY-10 | Compliant | Tags exatas/digest, imagens oficiais ou do projeto upstream, SBOM, sem LocalStack (sem dependencia de conta externa) |
| SECURITY-11 a 15 | N/A em U1 / cobertos nas unidades de servico e U6 | - |

Nenhum achado bloqueante.
