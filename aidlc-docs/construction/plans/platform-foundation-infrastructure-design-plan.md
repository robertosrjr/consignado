# Infrastructure Design Plan - U1 platform-foundation

Escopo de infraestrutura de U1: **ambiente local reproduzivel** (Compose) e politica de imagens de container. A infraestrutura AWS implantavel (Terraform) e responsabilidade de U6; U3-U5 definem seus mapeamentos AWS nos proprios Infrastructure Designs.

Entradas: `nfr-requirements.md`, `nfr-design/*` (aprovados). Functional Design de U1: SKIP.

## Fato novo verificado (impacta Q1)
Desde o LocalStack **2026.03.0** a imagem padrao exige **auth token** para iniciar; o plano gratuito (Hobby) e para **uso nao comercial**, e uso comercial e pago. Como o contexto e de instituicao financeira, uma dependencia de licenca/conta externa precisa ser decidida de forma explicita. Isso altera o item "LocalStack" dos requisitos (Q1-B) e do `nfr-design`.

Fontes: [Pricing & Packaging do LocalStack](https://blog.localstack.cloud/2026-upcoming-pricing-changes/), [Licensing](https://docs.localstack.cloud/aws/licensing/).

## Plano de Execucao
- [x] Passo 1: Analisar artefatos de design de U1 e verificar licenciamento do emulador AWS
- [x] Passo 2: Coletar respostas das perguntas abaixo
- [x] Passo 3: Gerar `construction/platform-foundation/infrastructure-design/infrastructure-design.md`
- [x] Passo 4: Gerar `construction/platform-foundation/infrastructure-design/deployment-architecture.md`
- [x] Passo 5: Avaliar conformidade SECURITY e apresentar resumo (nao ha shared-infrastructure AWS em U1; ficara em U6)

## Perguntas

Cada pergunta traz uma **Sugestao** da IA. Para aceitar, copie a letra para `[Answer]:`.

### Q1: Emulacao AWS local (DynamoDB, SQS, KMS)
Como emular os servicos AWS no ambiente local e nos testes de integracao?

A) **Emuladores sem conta/token**: **DynamoDB Local** (imagem oficial da AWS, com Streams), **ElasticMQ** (API compativel com SQS) e **local-kms** (API compativel com KMS) + PostgreSQL; Lambda **nao** emulado (ver Q2)
B) **LocalStack** com auth token (plano Hobby gratuito, nao comercial; uso comercial pago), cobrindo DynamoDB, SQS, KMS e Lambda
C) Mistura: LocalStack apenas para o que os outros nao cobrem
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Sem dependencia de conta, token ou licenca comercial (relevante para um banco); imagens leves e fixaveis por tag/digest (SECURITY-10); DynamoDB Local suporta Streams, que o Dispatcher precisa. Contrapartida: maior numero de containers e fidelidade menor que a AWS real, o que ja estava previsto em A4 dos riscos (validacao final na AWS fica fora do escopo da PoC, Q1-B dos requisitos).

[Answer]: A

### Q2: Como o Dispatcher (Lambda) executa localmente?
Sem emulacao de Lambda, nada liga o DynamoDB Streams ao `InboxStreamHandler` no ambiente local e no k6.

A) Executor local **`local-runner`** dentro do modulo `dispatcher-lambda` (ativado so por perfil `local`/teste): le o Stream do DynamoDB Local e invoca o mesmo `InboxStreamHandler`; nao e empacotado no artefato Lambda
B) Executar o Lambda em container com o Runtime Interface Emulator e acionar manualmente
C) Se escolher LocalStack na Q1 (Lambda e event source mapping emulados), sem `local-runner`
D) Other (please describe after [Answer]: tag below)

**Sugestao: A** (consistente com Q1-A). Mantem o caminho de codigo real (mesmo handler), roda sem Docker extra e permite medir drenagem no k6. Fica registrado como componente de U4, fora do artefato implantado.

[Answer]: A

### Q3: Organizacao do Compose
Como organizar o `compose.yaml` para o dia a dia de desenvolvimento?

A) **Perfis**: `infra` (DynamoDB Local, ElasticMQ, local-kms, PostgreSQL, oauth2-mock, Prometheus, Grafana, Jaeger) sobe sempre; `apps` (servicos das unidades U2-U5) opcional; permite rodar os servicos pela IDE com a infra em containers e tudo em containers para o k6
B) Um unico perfil com tudo
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Fluxo de desenvolvimento mais rapido e o mesmo arquivo serve para a carga completa. Os Dockerfiles dos servicos entram nas unidades respectivas.

[Answer]: A

### Q4: Imagem base dos servicos (politica de container)
Qual imagem base e processo de build para os containers de Ingestion API, Worker e Mock Legacy (Fargate e Compose)?

A) **Eclipse Temurin 25 JRE** com tag exata (e digest quando disponivel), Dockerfile multi-stage, usuario nao-root, sistema de arquivos somente leitura quando possivel
B) Distroless Java 25
C) Spring Boot Buildpacks (`spring-boot:build-image`, Paketo)
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Simples, previsivel, imagem oficial mantida e compativel com Java 25; atende SECURITY-09/10 (sem `latest`, nao-root). Distroless pode ser avaliado depois para reduzir superficie.

[Answer]:A
