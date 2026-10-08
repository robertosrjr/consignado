# NFR Design Patterns - U1 platform-foundation

Concretiza os NFR-U1-01 a NFR-U1-26 (`nfr-requirements.md`) com as decisoes Q1-Q4 do plano de NFR Design (todas A).

## P1. Pipeline de Qualidade do Build (NFR-U1-01 a 06)

Ciclo Maven padrao, com cada gate em uma fase fixa:

| Fase | Gate | Falha o build quando |
|---|---|---|
| `validate` | Enforcer: JDK >= 25, Maven >= 3.9, `banDynamicVersions`, `requireReleaseDeps`, `dependencyConvergence` | Versao dinamica, `-SNAPSHOT` em dependencia, divergencia de versoes |
| `validate` | Spotless `check` (google-java-format) | Codigo fora do formato |
| `compile` | `maven.compiler.release=25`, lint `-Xlint:all -Werror` selecionado | Aviso relevante de compilacao |
| `test` | Surefire: `*Test`, `*Properties` (jqwik), `*ArchTest` | Qualquer teste falha |
| `verify` | JaCoCo `check`: domain/application/`shared` >= 80%, infrastructure >= 50% | Cobertura abaixo do minimo |
| `package` | CycloneDX (SBOM) | - (gera artefato) |

Perfis:

| Perfil | Conteudo | Requer |
|---|---|---|
| (padrao) | Fases acima | Apenas JDK e Maven |
| `integration` | Failsafe: `*IT` com Testcontainers (PostgreSQL, DynamoDB Local, ElasticMQ, local-kms) | Docker |
| `security` | OWASP Dependency-Check (falha com CVSS >= 7) | Acesso a base NVD (chave opcional) |

Taxonomia de testes (Q4-A, PBT-10): `*Test` = exemplos; `*Properties` = propriedades jqwik; `*ArchTest` = ArchUnit; `*IT` = integracao. Nomes distinguem exemplo de propriedade.

## P2. Value Object com Validacao Fail-Fast e `toString` Seguro (NFR-U1-20, 21)

- Todo value object de `shared` e `record` com construtor compacto que valida e lanca `IllegalArgumentException` com mensagem **sem o valor sensivel**.
- `Cpf`: normaliza digitos, valida tamanho e digitos verificadores; `toString()` retorna `***.***.***-NN` (apenas os dois ultimos digitos); acesso ao valor completo somente por `digits()`, usado exclusivamente por adaptadores que precisam do dado.
- `IdempotencyKey`: parse estrito de UUID; `toString()` canonico em minusculas.
- `TenantId`: allowlist de caracteres `[A-Za-z0-9_-]`, 1 a 32 caracteres (evita injecao em chaves, logs e tags).
- `Money`: `BigDecimal` nao negativo, escala 2, `HALF_EVEN`.
- Contrato de mensagem `ProposalMessage`: `record` com `schemaVersion`; serializacao Jackson por tipo explicito, **sem tipagem polimorfica** (SECURITY-13).

## P3. Auto-configuracao Comum de Observabilidade e Hardening (Q1-A; NFR-U1-13 a 16)

Modulo `platform-starter` (Spring Boot auto-configuration) aplicado por Ingestion API, Worker e Mock Legacy. Entrega **defaults** que cada servico pode sobrescrever por variavel de ambiente:

| Preocupacao | Padrao aplicado |
|---|---|
| Logs | `logging.structured.format.console=ecs` (JSON, stdout); campos `@timestamp`, `log.level`, `message`, `traceId`, `spanId`, `service.name`; sem arquivo local |
| Actuator minimo | Exposicao apenas de `health`, `info`, `metrics`, `prometheus`; `health.probes.enabled=true` (liveness/readiness); `show-details=never` por padrao |
| Metricas | Registry Prometheus; tags comuns `service`, `env`; `TenantTagPolicy` (P4) |
| Tracing | Micrometer Tracing + ponte OTel, OTLP; amostragem via `PLATFORM_TRACING_SAMPLING` (padrao 1.0 em local) |
| Twelve-Factor | `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=30s`, `spring.threads.virtual.enabled=true`, configuracao toda por env |
| Erros | `PlatformProblemDetailAdvice` base: `ProblemDetail` generico, sem stack trace, SQL ou nomes internos; excecao nao mapeada -> `500` generico e log do detalhe sem PII (fail closed) |
| Cabecalhos de erro | `Cache-Control: no-store` em respostas de erro; sem `Server`/versao em respostas (`server.server-header` vazio) |

O starter **nao** contem logica de negocio nem classes de dominio. O Dispatcher (Lambda, sem Spring) reproduz o mesmo formato de campos de log de forma manual (U4).

## P4. Politica de Cardinalidade de Tags de Tenant (Q3-A; NFR-U1-14)

`TenantTagPolicy` (no starter) decide o valor da tag `tenant`:

```
resolve(tenantId):
  se tenantId esta na allowlist configurada (PLATFORM_METRICS_TENANT_ALLOWLIST)
     e allowlist.size() <= PLATFORM_METRICS_TENANT_MAX (padrao 50)
       -> tenantId
  senao -> "other"
```

- A allowlist e configuracao (nao aprende em runtime), logo cardinalidade maxima = tamanho da allowlist + 1.
- Cenario de abuso considerado (SECURITY-11): um robo envia `X-Tenant-ID` aleatorio para explodir a cardinalidade no Prometheus; resultado: todos viram `other`, sem crescimento de series.
- Tenants desconhecidos continuam rastreaveis por log estruturado (campo `tenant.id` apenas quando a politica de logs permitir; tenant e tratado como potencialmente pessoal).

## P5. Cadeia de Suprimentos (Q3 do NFR Requirements; NFR-U1-07 a 12)

- Versoes exatas no `dependencyManagement` e `pluginManagement` do parent; modulos filhos **nao** declaram versao.
- `spring-boot-dependencies` 4.1.1 importado como BOM; AWS SDK BOM 2.55.12; demais versoes via propriedades `*.version` no parent.
- Apenas Maven Central (`<repositories>` do parent sem terceiros).
- `dependency:analyze` documentado para detectar dependencias nao usadas.
- Compose: tags exatas (sem `latest`); digest quando disponivel.

## P6. Compose Endurecido (NFR-U1-17 a 19)

- Redes dedicadas: `data` (PostgreSQL, DynamoDB Local, ElasticMQ, local-kms, servicos) e `observability` (Prometheus, Grafana, Jaeger, servicos); nenhum servico em ambas desnecessariamente.
- Portas publicadas apenas em `127.0.0.1`.
- `healthcheck` em todos os servicos de infraestrutura; `depends_on` com `condition: service_healthy`.
- Limites de CPU e memoria (`deploy.resources.limits`) em todos os servicos.
- Segredos de teste via `.env` (nao versionado) e `.env.example` versionado; os emuladores AWS locais usam credenciais de teste fixas documentadas como excecao de desenvolvimento local.
- `read_only: true` e `cap_drop: [ALL]` onde a imagem suportar; `no-new-privileges`.
- PostgreSQL local sem expor fora do loopback; TLS e criptografia em repouso sao exigidos apenas nos recursos implantados (Terraform, U6) - excecao de ambiente local documentada (SECURITY-01).

## P7. Emissor JWT Local (Q2-A; NFR-U1-19)

- Container de **mock OAuth2 server** no Compose (rede `data`), publicando JWKS e emitindo tokens com `iss`, `aud`, `exp`, `sub` e claim `tenant` configuraveis.
- Ingestion API valida assinatura, issuer, audience e expiracao com o Resource Server apontando para o JWKS local (`PLATFORM_JWT_JWKS_URI`); em producao basta trocar a URI.
- k6 obtem tokens uma vez por tenant antes da carga (setup do k6), fora do caminho quente, com `exp` maior que a duracao do teste.
- O mock so e iniciado pelo perfil `local` do Compose; nunca referenciado pelo Terraform.

## P8. Harness de Property-Based Testing (NFR-U1-23 a 26; PBT-02, 03, 07, 08, 09)

- jqwik 1.10.1 no parent (escopo `test`).
- `jqwik.properties` (padrao): `database=target/jqwik-database`, `reporting.usejunitplatform=true`, `defaultTries=1000`, `generation=RANDOMIZED`; shrinking habilitado (nao sobrescrito); a seed e impressa em toda execucao e em falha.
- Em CI: `-Djqwik.seed`/relatorio de seed arquivado; propriedade que falha deve virar teste de exemplo permanente (PBT-10).
- Geradores de dominio reutilizaveis no **test-jar de `shared`**: `Arbitraries` para `Cpf` valido/invalido, `IdempotencyKey`, `TenantId`, `Money` (limites: 0, valor maximo do contrato), `ProposalMessage` com `Lane` e `priorityScore` coerentes; inclui casos de borda (zero, maximo, caracteres nao ASCII em `TenantId` invalido).
- Propriedades de `shared`: round-trip JSON de `ProposalMessage`; round-trip `IdempotencyKey`; invariantes de `Money` e `Cpf` (NFR-U1-26).

## P9. Falha Segura e Resiliencia do Ambiente Local

- Healthchecks e `depends_on` garantem ordem de inicializacao (sem `sleep`).
- Servicos de aplicacao: probes de readiness dependem de dependencias criticas; liveness nao.
- Nenhum retry cego e configurado no starter; retry e responsabilidade de cada servico (skill de resiliencia).

## Rastreabilidade NFR -> Padrao

| NFR | Padrao |
|---|---|
| NFR-U1-01 a 06 | P1 |
| NFR-U1-07 a 12 | P5 |
| NFR-U1-13 a 16 | P3, P4 |
| NFR-U1-17 a 19 | P6, P7 |
| NFR-U1-20, 21 | P2 |
| NFR-U1-22 a 26 | P8 |

## Compliance - Security Baseline (U1 / NFR Design)

| Regra | Status | Observacao |
|---|---|---|
| SECURITY-01 | N/A em U1 (excecao local documentada) | P6; aplicada em U3-U6 |
| SECURITY-02 | N/A em U1 | ALB em U6 |
| SECURITY-03 | Compliant | P3 (logs ECS, correlacao, sem PII); P2 (`toString` seguro) |
| SECURITY-04 | N/A | Sem HTML |
| SECURITY-05 | Compliant | P2 (validacao por allowlist nos value objects) |
| SECURITY-06 | N/A em U1 | U6 |
| SECURITY-07 | Compliant | P6 (redes dedicadas, loopback, `cap_drop`) |
| SECURITY-08 | Compliant (parcial, base) | P7 (validacao real de JWT); aplicacao nos endpoints em U3 |
| SECURITY-09 | Compliant | P3 (Actuator minimo, erro generico, versoes suportadas), P6 (sem credenciais padrao) |
| SECURITY-10 | Compliant | P5 |
| SECURITY-11 | Compliant | P4 documenta cenario de abuso (cardinalidade de tenant) |
| SECURITY-12 | N/A | Sem senhas; sem segredos no codigo |
| SECURITY-13 | Compliant | P2 (sem tipagem polimorfica), P5 (checksums do Maven Central) |
| SECURITY-14 | N/A em U1 | U6 e U7 |
| SECURITY-15 | Compliant | P3 (handler global fail-closed) |

Nenhum achado bloqueante.

## Compliance - PBT (modo parcial)

| Regra | Status |
|---|---|
| PBT-02, PBT-03 | Compliant (P8) |
| PBT-07 | Compliant (geradores centralizados no test-jar de `shared`) |
| PBT-08 | Compliant (shrinking, seed impressa, banco de falhas, CI) |
| PBT-09 | Compliant (jqwik 1.10.1) |
| PBT-01, 04, 05, 06, 10 (consultivas) | PBT-01 e PBT-10 atendidas em P8/P1; 04, 05, 06 N/A em U1 |

Nenhum achado bloqueante.
