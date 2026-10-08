# Mudanca de Escopo: Execucao Local sem Docker

Decisao do usuario (2026-10-07): **Docker nao sera instalado nesta maquina.**

## O que isso afeta
- `compose.yaml` e os 9 servicos de infraestrutura local (nao podem rodar aqui).
- Testes de integracao com Testcontainers (perfil `integration`) e a execucao do k6 contra o sistema completo.
- Itens ja decididos que dependiam de Docker: Infrastructure Design (emuladores em containers), `local-runner` do Dispatcher, Prometheus/Grafana/Jaeger locais.

## O que continua possivel (verificado nesta maquina)

| Necessidade | Alternativa sem Docker | Disponibilidade |
|---|---|---|
| DynamoDB com Streams | **DynamoDBLocal** como biblioteca Java dentro da JVM | `com.amazonaws:DynamoDBLocal` 2.6.1 (Maven Central) |
| SQS com DLQ/redrive | **ElasticMQ** embarcado | `org.elasticmq:elasticmq-rest-sqs_2.13` 1.7.1 |
| PostgreSQL | **embedded-postgres** (Zonky; binarios Windows via Maven) | `io.zonky.test:embedded-postgres` 2.2.2, binarios 18.6.0 |
| KMS | nao ha emulador embarcavel | usar adaptador local AES-GCM (ver Q2) |
| Carga | **k6 2.2.0** ja instalado; servicos como processos JVM | instalado |
| Prometheus / Grafana / Jaeger | nao rodam sem Docker/instalacao propria | metricas continuam expostas em `/actuator/prometheus`; evidencia via k6 e Actuator |
| JDK 25, Maven 3.9.11, Terraform 1.13.4, AWS CLI | instalados | ok |

## Perguntas

Cada pergunta traz uma **Sugestao** da IA. Para aceitar, copie a letra para `[Answer]:`.

### Q1: Testes de integracao dos adaptadores (DynamoDB, SQS, PostgreSQL)
A) **Emuladores embarcados na JVM** (DynamoDBLocal, ElasticMQ, embedded-postgres) nos testes `*IT`; o perfil `integration` continua existindo, agora **sem Docker**; Testcontainers sai da stack
B) Sem testes de integracao de adaptadores (somente unitarios e PBT)
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Mantem a cobertura de integracao exigida (idempotencia no DynamoDB, outbox no PostgreSQL, DLQ no SQS) e roda aqui. Limitacao: fidelidade menor que a AWS real (ja prevista); DynamoDBLocal embarcado usa bibliotecas nativas (SQLite), que serao validadas ao gerar o U3.

[Answer]: A

### Q2: Cifra do payload (CPF) sem emulador de KMS
A) Porta `PayloadProtectionPort` com **dois adaptadores**: KMS (AWS; usado em U6/implantacao) e **AES-GCM com chave local** (perfil `local`/teste; chave por variavel de ambiente, nunca no codigo); o adaptador KMS e validado apenas por teste unitario com cliente simulado (sem emulador)
B) Somente AES-GCM com chave de configuracao em todos os ambientes (sem KMS)
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Mantem o requisito de KMS para a AWS sem depender de um emulador, e o fluxo local usa criptografia real (AES-GCM). O risco (adaptador KMS nao exercitado contra o servico) fica registrado e e coberto por teste unitario e pela validacao do Terraform.

[Answer]:A

### Q3: Como executar o sistema completo localmente (k6 e DoD em escala reduzida)
A) Novo modulo de ferramenta **`local-env`** (nao implantado): sobe DynamoDBLocal + ElasticMQ + PostgreSQL embarcados e cria tabela/filas; o Dispatcher roda via `local-runner` (mesmo handler) e os servicos sobem como **processos JVM**; scripts PowerShell iniciam tudo; k6 mede a borda e o Mock Legacy
B) Sem execucao ponta a ponta local: validar apenas cada unidade (unitarios, PBT, integracao embarcada) e entregar o k6 pronto sem executar
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** E a unica forma de comprovar aqui o comportamento do DoD (180 TPS no legado, idempotencia ponta a ponta, drenagem 70/30) em escala reduzida. O custo e um modulo extra (`local-env`) e scripts. Prometheus/Grafana/Jaeger ficam como opcionais para quem tiver Docker; a evidencia local vem do k6 e do Actuator.

[Answer]:A

### Q4: Artefatos Docker ja gerados em U1 (`compose.yaml`, `Dockerfile.service`, `infra/*`, `observability/*`)
A) **Manter como opcionais**, documentados como "para quem tiver Docker, nao validados"; fora do caminho critico
B) Remover do repositorio
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Sao necessarios para a implantacao futura (imagem Fargate) e como referencia de infraestrutura; custam pouco manter e nao bloqueiam nada. Os documentos de design serao ajustados para refletir o modo sem Docker como caminho principal.

[Answer]:

---

## Complemento: banco de dados em memoria na primeira fase

Decisao adicional do usuario (2026-10-07): **o banco de dados sera em memoria neste primeiro momento.**

### O que isso muda
- A persistencia relacional do Worker (`proposal`, `proposal_event`) passa a ter, na primeira fase, apenas um **adaptador em memoria** da porta `ProposalRepositoryPort` (o desenho hexagonal ja previa a troca).
- Saem do caminho critico: JPA/Hibernate, Flyway, driver PostgreSQL e `embedded-postgres` (a Q1 acima deixa de incluir PostgreSQL; o Aurora continua apenas no Terraform, U6).
- O DDL do SDD (`proposal`, `proposal_event`) fica como contrato de referencia para a fase seguinte.
- Riscos: dados perdidos ao reiniciar o Worker; a atomicidade de "estado + evento de outbox" precisa ser garantida pelo proprio adaptador (operacao sincronizada); a verificacao do DoD "1 registro no PostgreSQL" precisa de outra forma de medir (Q6).

### Q5: Abrangencia de "banco em memoria"
A) **Apenas a persistencia relacional** (`proposal`/`proposal_event`) em memoria; o DurableInbox continua no DynamoDB Local embarcado (que ja roda em memoria), pois e ele que prova a escrita condicional atomica
B) **Tambem o DurableInbox** em memoria (adaptador proprio, sem DynamoDB Local), eliminando o emulador do caminho
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** O DynamoDB Local em memoria ja atende a restricao e preserva a semantica real de `attribute_not_exists` e de Streams, que sao o centro da tese da PoC; trocar por um mapa em memoria enfraqueceria a prova de idempotencia. Se o DynamoDBLocal embarcado nao funcionar no Windows (validacao no U3), recaio em B e aviso.

[Answer]:

### Q6: Como verificar o DoD de idempotencia sem consultar o PostgreSQL
O DoD pede "50.000 requisicoes iguais resultam em exatamente 1 registro". Com o banco em memoria, como medir?

A) O Worker expoe **metricas** (contagem de propostas por status) e o Mock Legacy expoe **contadores de averbacoes unicas e duplicadas** (esperado: 1 e 0); o k6 consulta essas metricas ao final; testes de integracao consultam o repositorio em memoria diretamente
B) Endpoint administrativo protegido no Worker que lista registros
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Nao cria endpoint que exponha dados (LGPD/SECURITY-08), usa contadores ja previstos no Mock Legacy (`FR-5.4`) e e reprodutivel pelo k6. A contagem de registros no repositorio em memoria e verificada nos testes de integracao.

[Answer]:

---

## Complemento: observabilidade em um EC2 na AWS

Decisao adicional do usuario (2026-10-07): **as ferramentas de observabilidade serao instaladas em um EC2 na AWS em breve.**

### O que isso muda
- Prometheus, Grafana e Jaeger deixam de ser parte do ambiente local: saem do `compose.yaml` como caminho principal (ficam opcionais) e passam a ser destino **remoto**.
- Os servicos precisam **enviar** (ou expor) telemetria para esse EC2; o Dispatcher (Lambda) nao pode ser raspado por scrape, o que favorece envio por push.
- O Terraform (U6) precisa, no minimo, do contrato de rede com esse EC2 (security groups, portas, enderecos).
- Localmente, sem Docker e sem essa stack, a evidencia vem do Actuator e do k6; a exportacao OTLP fica desligada por padrao (propriedade de endpoint vazia), sem impacto no build.

### Q7: Quem provisiona o EC2 de observabilidade?
A) **Fora do escopo do Terraform da PoC** (voce instala e opera); a PoC entrega apenas o **contrato**: portas, endpoints, variaveis de configuracao e as regras de rede que o Terraform deve permitir
B) Incluir um modulo Terraform `observability-ec2` (EC2 + user-data instalando Prometheus, Grafana, Jaeger/OTel Collector)
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Voce ja planeja instalar; um modulo com user-data nao pode ser testado aqui (sem deploy, sem Docker) e dobraria o escopo de U6. O contrato documentado permite plugar seu EC2 sem retrabalho.

[Answer]:

### Q8: Modelo de coleta de metricas e traces
A) **Push via OTLP** para um OpenTelemetry Collector no EC2 (traces e metricas pelo mesmo protocolo; o Collector expoe as metricas ao Prometheus ou escreve nele); funciona para ECS Fargate **e** para a Lambda do Dispatcher; sem portas de Actuator expostas na rede
B) **Pull**: Prometheus no EC2 faz scrape de `/actuator/prometheus` nos servicos ECS (exige descoberta de servico, ex.: Cloud Map), e OTLP apenas para traces; a Lambda ficaria sem metricas por scrape
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Fargate tem IPs dinamicos (scrape exige descoberta de servico e portas abertas), e Lambda nao e raspavel. Push por OTLP unifica tudo e reduz a superficie de rede (SECURITY-07). O endpoint `/actuator/prometheus` continua existindo para uso local e como alternativa.

[Answer]:

### Q9: Seguranca do canal de telemetria e acesso as UIs
A) Telemetria por **rede privada** (mesma VPC), com security group permitindo OTLP (4317/4318) somente a partir dos security groups dos servicos; **sem IP publico** no EC2; acesso a Grafana e Jaeger por **SSM Session Manager (port-forward)**; TLS no canal OTLP como melhoria posterior, registrada como risco
B) Endpoints publicos com TLS e autenticacao
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Sem exposicao publica de dashboards de um sistema financeiro (SECURITY-07/08) e sem gerenciar certificados na PoC. O risco de trafego OTLP sem TLS dentro da VPC fica documentado e e tratavel depois.

[Answer]:

---

> **NOTA (2026-10-07): as perguntas Q7, Q8 e Q9 acima (observabilidade em um EC2) foram SUBSTITUIDAS por `topologia-3-ec2-questions.md` (T1 a T7)**, porque o usuario definiu a topologia em 3 EC2 (observabilidade, Elasticsearch e aplicacao). Nao responda Q7 a Q9 aqui. Q1 a Q6 continuam validas.
