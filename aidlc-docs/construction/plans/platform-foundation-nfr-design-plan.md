# NFR Design Plan - U1 platform-foundation

Entrada: `nfr-requirements.md` e `tech-stack-decisions.md` de U1 (aprovados). Objetivo: padroes e componentes logicos que concretizam os NFR-U1-01 a NFR-U1-26.

## Plano de Execucao
- [x] Passo 1: Analisar NFR Requirements de U1
- [x] Passo 2: Coletar respostas das perguntas abaixo
- [x] Passo 3: Gerar `construction/platform-foundation/nfr-design/nfr-design-patterns.md`
- [x] Passo 4: Gerar `construction/platform-foundation/nfr-design/logical-components.md`
- [x] Passo 5: Avaliar conformidade SECURITY e PBT e apresentar resumo

## Perguntas

Cada pergunta traz uma **Sugestao** da IA. Para aceitar, copie a letra para `[Answer]:`.

### Q1: Onde vive a configuracao comum de observabilidade e seguranca (Spring)?
O `shared` nao pode depender de Spring (decisao do Application Design). Ingestion API, Worker e Mock Legacy precisam do mesmo padrao de logs ECS, Actuator minimo, Micrometer/Prometheus, tracing OTLP e tratamento global de erros.

A) Novo modulo **`platform-starter`** (Spring Boot auto-configuration) usado por Ingestion API, Worker e Mock Legacy: logging ECS, Actuator minimo, tags comuns de metricas, propagacao de trace, `ProblemDetail` base; sem logica de negocio
B) Cada servico mantem sua propria configuracao (copias de `application.yml`)
C) Arquivo comum de propriedades distribuido via parent Maven (resource filtering), sem codigo
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Evita tres copias divergentes (risco em SECURITY-03/09), mantem `shared` puro e o Dispatcher (sem Spring) fica fora. O modulo entra no reactor entre `shared` e os servicos.

[Answer]: A

### Q2: Emissor JWT local (JWKS) para o ambiente de desenvolvimento e k6
A Ingestion API valida assinatura, issuer, audience e expiracao. Como o ambiente local emite tokens?

A) Container **mock OAuth2 server** no Compose (JWKS e emissao de tokens por tenant com claims configuraveis); o k6 obtem tokens de teste uma vez por tenant antes da carga
B) Par de chaves de teste + endpoint JWKS estatico (arquivo servido por um container leve) e tokens pre-gerados por script
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Fluxo realista (issuer/audience/expiracao reais) sem codigo proprio; tokens emitidos fora do caminho quente do k6 para nao distorcer os 10.000 TPS. A imagem e a tag exata serao fixadas no Code Generation.

[Answer]:A

### Q3: Politica de tags de metricas por tenant (cardinalidade)
`ingestion.throttled.tenant.rate` precisa identificar o tenant, mas tags de alta cardinalidade sao proibidas pelas skills de metricas e LGPD.

A) Tag `tenant` apenas para uma **lista configuravel (allowlist) de tenants conhecidos**; os demais agregam em `tenant="other"`; limite maximo configuravel (padrao 50)
B) Tag `tenant` para todos (assume numero pequeno e fechado de parceiros)
C) Sem tag de tenant; diagnostico por log estruturado
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Mantem cardinalidade limitada mesmo com robo gerando `X-Tenant-ID` aleatorio (ataque de cardinalidade), preservando o dashboard por parceiro. O `tenantId` ja e tratado como potencialmente pessoal; so entra na metrica se estiver na allowlist.

[Answer]:A

### Q4: Taxonomia e execucao dos testes no build
Como separar testes para manter o build rapido sem Docker e executar PBT com seed registrada?

A) Surefire executa `*Test` e `*Properties` (jqwik) no build padrao; Failsafe executa `*IT` (Testcontainers) so no perfil `integration`; `*ArchTest` roda no build padrao; seed do jqwik impressa em toda execucao e banco de falhas em `target/`
B) Todos os testes no mesmo ciclo do Surefire, integracao incluida, exigindo Docker sempre
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Atende NFR-U1-06, PBT-08 e permite trabalhar hoje sem Docker. Convencao de nomes distingue testes por exemplo (`*Test`) de propriedades (`*Properties`), conforme PBT-10.

[Answer]:A
