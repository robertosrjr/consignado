# NFR Requirements Plan - U1 platform-foundation

Unidade: parent Maven (Java 25), modulo `shared`, `compose.yaml`, observabilidade base.
Functional Design de U1: **SKIP** (sem regra de negocio alem dos value objects de `shared`, detalhados no Code Generation; propriedades PBT de `Cpf`/`IdempotencyKey` serao registradas aqui e no codigo).

Extensoes ativas: **Security Baseline (todas as regras bloqueantes)** e **PBT parcial** (PBT-02, 03, 07, 08, 09 bloqueantes; demais consultivas).

## Fatos do ambiente local (verificados)
- JDK: OpenJDK 25.0.2 LTS (Microsoft build)
- Maven: 3.9.11
- Terraform: 1.13.4
- k6: 2.2.0
- **Docker / Docker Compose: nao encontrado no PATH** (nem em bash nem em PowerShell)

## Plano de Execucao
- [x] Passo 1: Analisar requisitos, design e ambiente local
- [x] Passo 2: Coletar respostas das perguntas abaixo
- [x] Passo 3: Gerar `construction/platform-foundation/nfr-requirements/nfr-requirements.md`
- [x] Passo 4: Gerar `construction/platform-foundation/nfr-requirements/tech-stack-decisions.md` (inclui framework PBT, PBT-09)
- [x] Passo 5: Avaliar conformidade SECURITY e PBT e apresentar resumo de compliance

## Perguntas

Cada pergunta traz uma **Sugestao** da IA. Para aceitar, copie a letra para `[Answer]:`.

### Q1: Docker ausente - como tratar testes de integracao e o Compose?
Testcontainers (LocalStack, PostgreSQL) e o `compose.yaml` exigem um runtime de containers; hoje nao ha nenhum instalado.

A) Gerar todo o codigo, testes de integracao e `compose.yaml` normalmente; **voce instala Docker Desktop (ou Rancher Desktop)** antes de executar as etapas de integracao e k6. Testes unitarios e PBT rodam sem Docker
B) Usar Podman como runtime (Testcontainers com socket compativel) e documentar a configuracao
C) Nao executar integracao nem Compose nesta maquina; apenas gerar e validar sintaxe (`docker compose config` fica para outro ambiente)
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** E o caminho padrao e mais simples; o plano (Q1-B dos requisitos) depende de LocalStack e Compose para validar a PoC. Testes de integracao ficam marcados e separados (perfil Maven `integration`), de modo que o build rapido funcione sem Docker.

[Answer]: A

### Q2: Versao do Spring Boot (Java 25)
Os documentos citam 3.3, que nao e adequada. Qual linha usar? (SECURITY-09 exige versoes atuais e suportadas; confirmarei a ultima patch e a compatibilidade com Java 25 diretamente no Maven Central ao gerar o `pom.xml`.)

A) Spring Boot **4.x**, ultima versao estavel (Spring Framework 7, suporte nativo ao Java 25)
B) Spring Boot **3.5.x**, ultima patch (maior compatibilidade de bibliotecas; verificar se ainda recebe correcoes)
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Projeto novo, alvo Java 25 e requisito de manter runtime suportado; a linha 3.5 e a mais antiga e pode estar fora do suporte aberto. Risco: compatibilidade de Resilience4j, springdoc e Micrometer com a 4.x; sera validada no build do parent antes de seguir. Se alguma biblioteca nao for compativel, volto a voce com a alternativa.

[Answer]: A

### Q3: Cadeia de suprimentos (SECURITY-10)
Maven nao tem lock file nativo. Como atender a "dependencias fixadas, scanner de vulnerabilidades e SBOM"?

A) Versoes exatas no `dependencyManagement` do parent (sem ranges) + **OWASP Dependency-Check** + **CycloneDX** (SBOM) no build + Maven Enforcer proibindo versoes dinamicas e `-SNAPSHOT` em dependencias
B) Somente versoes exatas e Enforcer, documentando scanner e SBOM como passo manual
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Atende a regra sem custo operacional relevante; o scanner roda no perfil `security`, nao no build rapido (pode exigir chave NVD, documentada nas instrucoes).

[Answer]:A

### Q4: Framework de property-based testing (PBT-09)
Qual framework para Java 25 com JUnit 5?

A) **jqwik** (JUnit Platform, shrinking automatico, seed reproduzivel, suporte a stateful)
B) junit-quickcheck
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Recomendado pela propria regra PBT-09 para Java, com shrinking e `seed` registrada em falhas (PBT-08). Geradores de dominio (`Cpf`, `Money`, `ProposalMessage`) ficam em utilitario reutilizavel de teste no `shared` (PBT-07).

[Answer]:A

### Q5: Formato de logs (SECURITY-03)
Logs devem ter timestamp, correlation id, nivel e mensagem, em stdout, sem PII.

A) **Logging estruturado nativo do Spring Boot** (JSON, formato ECS) para stdout, com `traceId`/`spanId` via MDC e SLF4J fluente; mascaramento obrigatorio (`Cpf.toString()` mascarado); sem dependencia extra
B) Logback + `logstash-logback-encoder` (JSON), controle total do layout
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Menos dependencias (SECURITY-10), integracao nativa com Micrometer Tracing e coerente com as skills de logging/tracing. O Lambda Dispatcher (sem Spring) usara o logger do runtime com o mesmo formato de campos.

[Answer]:A
