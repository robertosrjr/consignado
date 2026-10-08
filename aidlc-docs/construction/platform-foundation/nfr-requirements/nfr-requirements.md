# NFR Requirements - U1 platform-foundation

Escopo: parent Maven (Java 25), modulo `shared`, `compose.yaml`, observabilidade base. Origem: `requirements.md` (NFR-8 a NFR-15, FR-7, FR-9.1) e respostas Q1-Q5 do plano (todas A).

## 1. Decisoes das Perguntas

| Q | Decisao |
|---|---|
| Q1 | Gerar codigo, testes de integracao e Compose normalmente; **Docker nao esta instalado** nesta maquina e sera instalado pelo usuario antes das etapas de integracao e k6. Testes unitarios e PBT rodam sem Docker; integracao em perfil Maven separado (`integration`) |
| Q2 | Spring Boot **4.x**; versao fixada **4.1.1** (verificada no Maven Central em 2026-10-07) |
| Q3 | Versoes exatas no `dependencyManagement`, OWASP Dependency-Check, CycloneDX (SBOM), Enforcer sem versoes dinamicas nem `-SNAPSHOT` em dependencias |
| Q4 | **jqwik** como framework PBT |
| Q5 | Logging estruturado nativo do Spring Boot (JSON, ECS) em stdout, com `traceId`/`spanId` via MDC |

## 2. Requisitos Nao Funcionais da Unidade

### 2.1 Plataforma e build
- **NFR-U1-01** Compilar e executar em Java 25 (`maven.compiler.release=25`); Maven Enforcer exige JDK >= 25 e Maven >= 3.9.
- **NFR-U1-02** Reactor Maven unico com modulos na ordem `shared`, `mock-legacy`, `ingestion-api`, `dispatcher-lambda`, `worker` (ordem de dependencia em `unit-of-work-dependency.md`).
- **NFR-U1-03** Spotless (formatacao) falha o build em violacao; `-Werror` para avisos de compilacao relevantes (lint basico).
- **NFR-U1-04** JaCoCo com gates: domain e application >= 80%, infrastructure >= 50% (nos modulos que os possuem); `shared` >= 80%.
- **NFR-U1-05** ArchUnit disponibilizado como dependencia de teste no parent para as regras de U3 e U5.
- **NFR-U1-06** Build rapido (sem Docker): testes unitarios e PBT; perfil `integration` (Testcontainers) e perfil `security` (Dependency-Check) separados.

### 2.2 Cadeia de suprimentos (SECURITY-10)
- **NFR-U1-07** Todas as dependencias e plugins com versao exata, centralizadas no `dependencyManagement`/`pluginManagement` do parent; sem ranges.
- **NFR-U1-08** Enforcer: `requireReleaseDeps`, `banDynamicVersions`, `dependencyConvergence`.
- **NFR-U1-09** OWASP Dependency-Check (perfil `security`; falha com CVSS >= 7). Chave da NVD opcional, documentada nas instrucoes.
- **NFR-U1-10** SBOM CycloneDX gerado no `package`.
- **NFR-U1-11** Dependencias apenas do Maven Central; sem repositorios de terceiros; remover dependencias nao usadas (`dependency:analyze` no CI de revisao).
- **NFR-U1-12** Imagens do `compose.yaml` com tag exata (sem `latest`); digest quando disponivel.

### 2.3 Observabilidade base (FR-7)
- **NFR-U1-13** Padrao de logs: JSON ECS em stdout, campos `@timestamp`, `log.level`, `message`, `traceId`, `spanId`, `service.name`; sem payload, sem PII; `Cpf.toString()` mascarado.
- **NFR-U1-14** Micrometer com registry Prometheus, endpoint `/actuator/prometheus` protegido/expondo apenas `health`, `info`, `metrics`, `prometheus` (Actuator minimo); tags de baixa cardinalidade.
- **NFR-U1-15** Tracing via Micrometer Tracing + ponte OpenTelemetry, exportador OTLP; amostragem por variavel de ambiente (padrao 1.0 em local, configuravel); coletor opcional.
- **NFR-U1-16** Prometheus (scrape de 5 s em local), Grafana com datasource provisionado e Jaeger no Compose.

### 2.4 Ambiente local (FR-9.1)
- **NFR-U1-17** `compose.yaml` conforme Compose Specification (sem `version`), com `healthcheck`, `depends_on` com `condition: service_healthy`, limites de CPU/memoria, redes dedicadas e portas expostas apenas em `127.0.0.1`.
- **NFR-U1-18** Sem credenciais hardcoded: segredos de teste via `.env` (nao versionado; `.env.example` versionado) ou `secrets`; emuladores AWS locais usam credenciais de teste documentadas como excecao de ambiente local.
- **NFR-U1-19** Emissor JWT mock local (JWKS) para validacao real no U3.

### 2.5 Qualidade do modulo `shared`
- **NFR-U1-20** Value objects imutaveis (`record`), validacao no construtor, `toString()` seguro (sem PII).
- **NFR-U1-21** Sem dependencia de Spring, AWS SDK ou logica de negocio em `shared`.
- **NFR-U1-22** Utilitario de testes reutilizavel (`shared` test-jar) com geradores jqwik de dominio (`Cpf`, `IdempotencyKey`, `TenantId`, `Money`, `ProposalMessage`).

### 2.6 PBT (extensao parcial)
- **NFR-U1-23** (PBT-09) jqwik 1.10.1 declarado no parent e documentado em `tech-stack-decisions.md`.
- **NFR-U1-24** (PBT-08) Shrinking habilitado; `jqwik.properties` com `database` de falhas e registro da `seed` em falha; CI registra a seed em toda execucao.
- **NFR-U1-25** (PBT-07) Geradores de dominio centralizados e reutilizaveis.
- **NFR-U1-26** (PBT-02, PBT-03) Propriedades a cobrir em `shared`: round-trip de serializacao de `ProposalMessage` (JSON), round-trip `IdempotencyKey.parse(k.toString())`, invariantes de `Money` (nao negativo, 2 casas, soma) e de `Cpf` (digitos validos aceitos, invalidos rejeitados).

## 3. Riscos e Pendencias

| Id | Risco | Tratamento |
|---|---|---|
| R1 | Docker ausente nesta maquina | Perfil `integration` separado; usuario instala Docker antes de U3-U7 (ou de qualquer execucao de integracao). Validar com `docker info` antes dessas etapas |
| R2 | Compatibilidade de bibliotecas com Spring Boot 4.1.1 | Resilience4j (`resilience4j-spring-boot4` 2.4.0) e springdoc 3.1.1 existem; validar via `mvn verify` do parent no Code Generation |
| R3 | Tags de imagens (Prometheus, Grafana, Jaeger, DynamoDB Local, ElasticMQ, local-kms, mock OAuth2, PostgreSQL) | Fixadas no Code Generation consultando os registries; sem Docker local nao e possivel `pull` de verificacao |
| R4 | Dependency-Check requer download da base NVD (lento, rate limit) | Perfil `security` opcional no build local; documentado |

## 4. Compliance - Security Baseline (U1)

| Regra | Status | Observacao |
|---|---|---|
| SECURITY-01 Criptografia em repouso e em transito | N/A em U1 | U1 nao define repositorios de dados implantados; PostgreSQL/DynamoDB locais do Compose sao ambiente de desenvolvimento (excecao documentada); a regra e aplicada em U3-U6 e no Terraform |
| SECURITY-02 Access logging em intermediarios | N/A em U1 | ALB/WAF sao de U6 |
| SECURITY-03 Logging de aplicacao | Compliant (requisito) | NFR-U1-13: JSON estruturado, correlacao, sem PII |
| SECURITY-04 Headers HTTP | N/A | Nao ha endpoints HTML |
| SECURITY-05 Validacao de entrada | Compliant (requisito) | Value objects validam na construcao (NFR-U1-20); aplicacao nos endpoints em U2 e U3 |
| SECURITY-06 Privilegio minimo | N/A em U1 | IAM em U6; Compose sem permissoes AWS reais |
| SECURITY-07 Rede restritiva | Compliant (requisito) | NFR-U1-17: redes dedicadas, portas apenas em loopback |
| SECURITY-08 Controle de acesso | N/A em U1 | Emissor JWT mock (NFR-U1-19); validacao em U3 |
| SECURITY-09 Hardening | Compliant (requisito) | Actuator minimo, Spring Boot 4.1.1 suportado, Java 25 LTS, sem credenciais padrao (NFR-U1-18) |
| SECURITY-10 Cadeia de suprimentos | Compliant (requisito) | NFR-U1-07 a 12. Maven nao tem lock file nativo; a regra aceita "versoes exatas ou lock files" e e atendida por versoes exatas + Enforcer |
| SECURITY-11 Design seguro | N/A em U1 | Rate limiting e abuso em U3 |
| SECURITY-12 Autenticacao e credenciais | N/A | Sem login/senha; sem segredos no codigo (NFR-U1-18) |
| SECURITY-13 Integridade | Compliant (requisito) | Artefatos do Maven Central com checksums; sem desserializacao insegura (somente DTOs/records, sem tipagem polimorfica) |
| SECURITY-14 Alertas e monitoramento | N/A em U1 | Dashboards/alarmes em U7 e U6 |
| SECURITY-15 Tratamento de excecoes | N/A em U1 | Handlers globais em U2, U3, U5 |

Nenhum achado bloqueante.

## 5. Compliance - PBT (modo parcial)

| Regra | Status | Observacao |
|---|---|---|
| PBT-01 Identificacao de propriedades (consultiva) | Compliant | Propriedades de `shared` listadas em NFR-U1-26 |
| PBT-02 Round-trip (bloqueante) | Compliant (requisito) | `ProposalMessage` JSON e `IdempotencyKey` |
| PBT-03 Invariantes (bloqueante) | Compliant (requisito) | `Money`, `Cpf` |
| PBT-07 Qualidade de geradores (bloqueante) | Compliant (requisito) | NFR-U1-22/25 |
| PBT-08 Shrinking e reprodutibilidade (bloqueante) | Compliant (requisito) | NFR-U1-24 |
| PBT-09 Framework (bloqueante) | Compliant | jqwik 1.10.1, ver `tech-stack-decisions.md` |
| PBT-04, 05, 06, 10 (consultivas) | N/A em U1 | Idempotencia, oraculo e stateful sao aplicaveis em U4/U5; PBT-10 seguido por exemplos pontuais em cada classe |

Nenhum achado bloqueante.
