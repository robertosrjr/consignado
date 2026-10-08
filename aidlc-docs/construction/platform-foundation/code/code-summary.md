# Code Summary - U1 platform-foundation

Codigo em `applications/` (raiz da workspace). Este arquivo e apenas um resumo; nenhum codigo de aplicacao fica em `aidlc-docs/`.

## Resultado da Verificacao (executada)

`mvn verify` no reactor (JDK 25.0.2, Maven 3.9.11): **BUILD SUCCESS**.

| Modulo | Testes | Cobertura de linhas | Cobertura de ramos |
|---|---|---|---|
| shared | 45 (exemplos, propriedades jqwik, ArchUnit) | 93,3% (gate 80%) | 97,1% |
| platform-starter | 19 | 96,4% (gate 80%) | 100% |

Gates ativos e aprovados: Enforcer (JDK >= 25, Maven >= 3.9, sem versoes dinamicas, `requireReleaseDeps`, `dependencyConvergence`), Spotless (google-java-format 1.36.1), compilacao `--release 25 -Xlint:all -Werror`, JaCoCo, SBOM CycloneDX gerado.
Nao executado: perfis `integration` (requer Docker) e `security` (Dependency-Check, requer NVD); `docker compose config/up` (Docker ausente).

## Arquivos Criados

### Build
- `applications/pom.xml` (parent: BOMs Spring Boot 4.1.1 e AWS SDK 2.55.12, plugins, gates, perfis `integration` e `security`)
- `applications/.gitignore`, `.dockerignore`, `.env.example`, `README.md`

### Modulo `shared` (`applications/shared/`)
- `vo/Cpf`, `vo/IdempotencyKey`, `vo/TenantId`, `vo/Money`
- `contract/ConvenioType`, `Lane`, `ProposalMessage`, `ProposalMessageCodec`, `MessageContractException`, `InboxAttributes`, `HeaderNames`
- Testes: `CpfTest`, `CpfProperties`, `IdempotencyKeyTest`, `IdempotencyKeyProperties`, `TenantIdTest`, `MoneyTest`, `MoneyProperties`, `ProposalMessageTest`, `ProposalMessageCodecTest`, `ProposalMessageCodecProperties`, `SharedArchTest`
- Test-jar: `testkit/DomainArbitraries` (geradores jqwik reutilizaveis)
- `src/test/resources/jqwik.properties`

### Modulo `platform-starter` (`applications/platform-starter/`)
- `PlatformDefaultsEnvironmentPostProcessor`, `PlatformAutoConfiguration`, `PlatformWebAutoConfiguration`, `PlatformProblemDetailAdvice`, `TenantTagPolicy`, `TenantMetricsProperties`
- Registro: `META-INF/spring.factories`, `META-INF/spring/...AutoConfiguration.imports`
- Testes: `PlatformDefaultsEnvironmentPostProcessorTest`, `PlatformAutoConfigurationTest`, `PlatformProblemDetailAdviceTest`, `TenantTagPolicyTest`, `TenantTagPolicyProperties`, `StarterArchTest`

### Ambiente local e infraestrutura
- `applications/compose.yaml` (9 servicos: dynamodb-local, elasticmq, local-kms, postgres, oauth2-mock, bootstrap, prometheus, grafana, jaeger)
- `applications/infra/bootstrap/init.sh`, `infra/elasticmq/elasticmq.conf`, `infra/oauth2-mock/oauth2-mock.json`
- `applications/observability/prometheus/prometheus.yml`, `observability/grafana/provisioning/{datasources,dashboards}/*.yml`, `observability/grafana/dashboards/README.md`
- `applications/docker/Dockerfile.service` (modelo multi-stage, Temurin 25 JRE, nao-root)

## Decisoes e Desvios (todos documentados)

| Item | Decisao |
|---|---|
| google-java-format | Versao fixada de forma literal (1.36.1); referencia por propriedade falhou no Spotless 3.10.3 |
| Jackson | Spring Boot 4 usa Jackson 3 (`tools.jackson`); `shared` usa `JsonMapper` explicito, sem tipagem polimorfica |
| `shared` e JSON | O codec do `ProposalMessage` fica em `shared` (usado por Dispatcher e Worker); a dependencia e Jackson, sem Spring |
| Tracing e metricas no starter | O starter define apenas defaults (propriedades); cada servico adiciona as dependencias de Actuator, Micrometer/Prometheus e OpenTelemetry (U2 a U5). Evita impor dependencias a servicos que nao as usam |
| Healthchecks do Compose | Nao validados (Docker ausente) |
| `read_only` nos containers | Nao aplicado (validar caso a caso); `cap_drop: ALL` e `no-new-privileges` aplicados |
| ElasticMQ | Nao aplica retencao de 14 dias da DLQ (requisito do Terraform, U6) |

## Compliance - Security Baseline (Code Generation de U1)

| Regra | Status | Evidencia |
|---|---|---|
| SECURITY-01 | N/A (excecao local documentada) | Emuladores locais sem TLS/at-rest; requisito aplicado em U6 |
| SECURITY-02 | N/A | ALB/WAF em U6 |
| SECURITY-03 | Compliant | Defaults de log ECS/JSON em stdout; `Cpf.toString()` mascarado (teste `to_string_never_reveals...`); handler registra evento sem payload |
| SECURITY-04 | N/A | Sem HTML |
| SECURITY-05 | Compliant | Value objects validam por allowlist/limites (`TenantId`, `Cpf`, `IdempotencyKey`, `Money`); testes de propriedades com entradas invalidas |
| SECURITY-06 | N/A | IAM em U6 |
| SECURITY-07 | Compliant | Compose: portas em 127.0.0.1 (verificado por script), redes dedicadas, `cap_drop: ALL` |
| SECURITY-08 | N/A em U1 | Aplicado em U3 (JWT); emissor local pronto |
| SECURITY-09 | Compliant | Spring Boot 4.1.1, Java 25, Actuator minimo (teste), erros sem detalhes (testes), sem credenciais padrao (`.env` exigido), imagens sem `latest` (verificado por script) |
| SECURITY-10 | Compliant | Versoes exatas + Enforcer (`banDynamicVersions`), SBOM CycloneDX, perfil `security`, tags exatas no Compose. Observacao: scan Dependency-Check nao foi executado nesta etapa |
| SECURITY-11 | N/A em U1 | Politica de tenant no starter cobre cardinalidade; rate limiting em U3 |
| SECURITY-12 | Compliant | Sem segredos no codigo; credenciais AWS de teste (`test`) sao fictícias e documentadas como excecao local |
| SECURITY-13 | Compliant | Sem desserializacao polimorfica (Jackson com tipo explicito), campos desconhecidos ignorados sem executar codigo |
| SECURITY-14 | N/A | Alarmes e dashboards em U6/U7 |
| SECURITY-15 | Compliant | `PlatformProblemDetailAdvice` global, fail closed (teste) |

Nenhum achado bloqueante.

## Compliance - PBT (modo parcial)

| Regra | Status | Evidencia |
|---|---|---|
| PBT-02 (round-trip) | Compliant | `ProposalMessageCodecProperties` (JSON), `IdempotencyKeyProperties`, `MoneyProperties` (string), `CpfProperties` (formatacao) |
| PBT-03 (invariantes) | Compliant | `MoneyProperties`, `CpfProperties`, `TenantTagPolicyProperties` (cardinalidade <= allowlist + 1) |
| PBT-07 (geradores) | Compliant | `DomainArbitraries` (CPF valido/invalido, TenantId, Money com bordas, ProposalMessage) reutilizado via test-jar |
| PBT-08 (shrinking e seed) | Compliant | Shrinking padrao do jqwik; `reportOnlyFailures=false` imprime seed em toda execucao (verificado na saida); banco de falhas em `target/jqwik-database` |
| PBT-09 (framework) | Compliant | jqwik 1.10.1 no parent |
| PBT-01, 04, 05, 06, 10 (consultivas) | PBT-10 atendida (exemplos `*Test` ao lado de `*Properties`); demais N/A em U1 |

Nenhum achado bloqueante.

## Pendencias para as proximas unidades
1. Instalar Docker/Rancher Desktop antes de U2 e executar `docker compose config` / `up` (validar healthchecks).
2. U2 em diante: cada servico declara `<finalName>app</finalName>` e as dependencias de Actuator/Micrometer/OpenTelemetry.
3. Executar `mvn verify -Psecurity` quando houver chave NVD.
