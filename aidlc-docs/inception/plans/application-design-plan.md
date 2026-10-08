# Application Design Plan

Contexto: `requirements.md` (aprovado) e `execution-plan.md` (aprovado). User Stories foi pulado. Esta etapa define componentes, metodos (assinaturas), servicos e dependencias em alto nivel; regras de negocio detalhadas ficam no Functional Design por unidade.

## Plano de Execucao

- [x] Passo 1: Analisar contexto (requirements, execution plan, skills de arquitetura/API/resiliencia)
- [x] Passo 2: Coletar respostas das perguntas abaixo (decisoes de fronteira de componentes)
- [x] Passo 3: Gerar `application-design/components.md` (componentes, responsabilidades, interfaces; Ingestion API e Worker em Hexagonal, Dispatcher e Mock Legacy simples)
- [x] Passo 4: Gerar `application-design/component-methods.md` (assinaturas, entradas e saidas)
- [x] Passo 5: Gerar `application-design/services.md` (casos de uso e orquestracao)
- [x] Passo 6: Gerar `application-design/component-dependency.md` (matriz de dependencias, padroes de comunicacao, fluxo de dados)
- [x] Passo 7: Validar completude e consistencia (rastreio FR/NFR, regra de dependencia domain -> application -> infrastructure)

## Perguntas

Cada pergunta traz uma **Sugestao** da IA. Para aceitar, copie a letra para `[Answer]:`.

### Q1: Quem calcula o `priorityScore`?
O FR-2.1 lista `priorityScore` no DurableInbox, mas o FR-3.2 diz que o Dispatcher calcula a prioridade. Onde fica a regra?

A) Somente o Dispatcher calcula e persiste `priorityScore` (a Ingestion API grava `convenioType` e `isPortability`, sem pontuar); a borda fica mais rapida e a regra muda sem tocar na API
B) A Ingestion API calcula na borda e grava `priorityScore`; o Dispatcher apenas le e roteia
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Mantem a borda minima (SLA p95 < 100 ms) e concentra a regra de prioridade em um unico componente puro e testavel (PBT). A regra pode evoluir sem redeploy da API.

[Answer]:A

### Q2: Biblioteca `shared` - o que entra?
Qual o conteudo do modulo `shared`, usado pelos servicos?

A) Somente contratos e utilitarios neutros: DTOs de mensagem SQS/Stream, enums (`ConvenioType`, `ProposalStatus`), constantes de headers e propagacao de trace; sem logica de negocio nem dependencia de Spring
B) Tambem o dominio comum (value objects como `Cpf`, `Money`, `IdempotencyKey`)
C) Nao criar `shared`; cada servico define seus proprios tipos
D) Other (please describe after [Answer]: tag below)

**Sugestao: B.** Value objects (`Cpf`, `IdempotencyKey`, `Money`) sao usados por Ingestion API, Dispatcher e Worker; centralizar evita divergencia de validacao. Continuam sem Spring (records Java 25) e o contrato de mensagem fica versionado em um so lugar.

[Answer]:B

### Q3: Orcamento 70/30 - emprestimo de capacidade entre lanes
Se uma lane esta vazia, a outra pode usar a capacidade ociosa (respeitando o teto de 180 TPS)?

A) Sim, com prioridade garantida: Fast tem ate 126 TPS e Standard ate 54 TPS como piso garantido; capacidade ociosa de uma lane e emprestada a outra (teto total 180)
B) Nao, teto rigido por lane (126 e 54), mesmo com a outra vazia
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** O DoD exige drenagem "mantendo a proporcao 70/30" sob backlog nas duas lanes e protege o legado a 180; com uma lane vazia, desperdicar capacidade so alonga o `effective_drain_time`. O emprestimo vira um componente (`LaneBudgetAllocator`) testavel com PBT.

[Answer]:A

### Q4: Coordenacao do teto global de 180 TPS entre workers
O limiter Resilience4j e local por instancia. Como o componente de orcamento sera desenhado em alto nivel (detalhe no NFR Design)?

A) Porta `ThroughputBudgetPort` com implementacao inicial por **orcamento fixo por tarefa** (180 / N tarefas, N configuravel), substituivel por contador distribuido depois
B) Contador distribuido desde ja (ex.: DynamoDB/Redis) como unica implementacao
C) Uma unica tarefa Worker na PoC, limiter local
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Simples, sem novo componente de infraestrutura, e a porta deixa a evolucao aberta. Limitacao documentada: com tarefas em numero diferente de N o teto efetivo desvia; o Mock Legacy continua medindo o pico real por janela de 1 s.

[Answer]:A

### Q5: Contrato de chamada do Worker ao Mock Legacy
Como o Worker e o Mock Legacy tratam a idempotencia da averbacao?

A) `POST /v1/averbacoes` com header `Idempotency-Key`; o Mock Legacy devolve o mesmo resultado para chave repetida (`200`) e `503` acima de 180 TPS
B) Sem idempotencia no legado; o Worker garante unicidade apenas pelo Aurora
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Fecha a janela de averbacao duplicada quando o Worker cai apos o legado e antes de gravar o resultado (requisito D11 e FR-5.4). Nome do recurso e ajustavel no Functional Design.

[Answer]:A

### Q6: Pacote base e identificadores Maven
Qual o `groupId` / pacote base dos modulos?

A) `br.com.consignado.platform` (artifacts: `consignado-ingestion-api`, `consignado-worker`, ...)
B) `com.empresa.consignado` (placeholder das skills)
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Nome de dominio coerente com o projeto; o placeholder `com.empresa` das skills nao deve ir para o codigo.

[Answer]:A
