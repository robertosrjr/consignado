# Skill: Arquitetura BIAN para Projetos Financeiros

Este documento orienta o agente a modelar, revisar e implementar sistemas bancários alinhados ao **BIAN (Banking Industry Architecture Network)**, versão de referência **14.0** (fevereiro de 2026). O catálogo completo de domínios está em [`service-landscape-v14.md`](service-landscape-v14.md).

> **Fontes e confiança.** A hierarquia e os nomes de Service Domains, assim como as mudanças da v14.0, vêm do portal e das release notes oficiais. As tabelas de Functional Patterns (seção 4) e de Action Terms (seção 5) consolidam o padrão publicado desde a v8.0. São referência de trabalho: antes de fixar um contrato, confirme o par exato no Service Domain do portal ou no YAML da Semantic API.

---

## 1. O que é o BIAN e por que usar

O BIAN é uma associação sem fins lucrativos de bancos, fornecedores e academia que publica uma **arquitetura de referência neutra de tecnologia** para o setor bancário. Ela decompõe a operação de um banco em **Service Domains** elementares, mutuamente exclusivos e coletivamente exaustivos (MECE). Cada Service Domain tem uma única responsabilidade de negócio e uma interface de serviço padronizada.

Ganhos ao seguir o padrão:

- **Vocabulário comum** entre negócio, arquitetura, fornecedores e reguladores.
- **Fronteiras de componente estáveis**, que viram candidatas naturais a bounded context e microsserviço.
- **Interfaces padronizadas** (Semantic APIs em OpenAPI 3 e AsyncAPI 3, com BOM e ISO 20022), que reduzem o custo de integração.
- **Modernização incremental do legado** ("coreless"): é possível isolar um Service Domain de cada vez, sem reescrever o core.

---

## 2. Metamodelo essencial

| Conceito | Definição | Exemplo |
|---|---|---|
| **Business Area** | Agrupamento de mais alto nível. São 5 na v14: Reference Data, Sales and Service, Operations and Execution, Risk and Compliance, Business Support. | Operations and Execution |
| **Business Domain** | Conjunto coeso de Service Domains que compartilham competência e conhecimento. | Loans and Deposits |
| **Service Domain (SD)** | Menor capacidade de negócio autônoma. Combina **um** Functional Pattern com **um** Asset Type e gerencia o ciclo de vida completo das instâncias do seu Control Record. | Consumer Loan |
| **Asset Type** | O objeto de negócio sobre o qual o SD atua. | Consumer loan |
| **Functional Pattern (FP)** | O comportamento padronizado que o SD aplica ao asset (Fulfill, Assess, Agree Terms...). | Fulfill |
| **Generic Artifact** | Tipo de registro que o FP produz. | Fulfillment Arrangement |
| **Control Record (CR)** | O registro que o SD controla: Asset Type + Generic Artifact. É **um por SD**. | Consumer Loan Fulfillment Arrangement |
| **Behavior Qualifier (BQ)** | Subdivisão funcional do CR, usada para detalhar operações. Os BQs foram racionalizados na v14. | Disbursement, Repayment, Financial Status Tracking |
| **Service Operation** | Operação exposta pelo SD sobre o CR ou um BQ, identificada por um **Action Term**. | Initiate, Update, Execute, Retrieve |
| **Service Exchange / Connection** | Chamada de um SD para outro, com Action Term e propósito. | Consumer Loan → Customer Credit Rating: Evaluate |
| **Business Scenario** | Sequência de exchanges entre SDs que realiza um evento de negócio (UML Sequence). | External Credit Transfer |
| **Wireframe** | Diagrama de SDs e conexões típicas de uma área, ponto de partida para cenários próprios. | BIAN Payments Hub Wireframe |
| **Business Capability** | Mapa de capacidades (BCM), ligado aos SDs. | — |
| **BOM (Business Object Model)** | Modelo canônico de objetos e atributos (UML) usado nas APIs. | Loan, Party, Arrangement |
| **Semantic API** | Especificação lógica da interface de um SD. São 242 na v14, publicadas em 4 sabores (REST/Async × BOM/ISO 20022). | ConsumerLoan OAS 3 |

**Notação:** o landscape e as capacidades são expressos em ArchiMate 3.2. CR, BOM e cenários são expressos em UML.

---

## 3. Regras de ouro do modelador

1. **Comece pelo landscape, não pelo sistema.** Localize o SD pelo que o negócio faz (asset + comportamento), e não pelo nome do sistema atual. Sistema legado ≠ Service Domain.
2. **Um SD, um Control Record.** Se um componente controla dois CRs diferentes, ele contém dois SDs. Se dois componentes disputam o mesmo CR, há sobreposição a resolver.
3. **SDs são MECE.** Não crie um SD "novo" para algo que o landscape já cobre. Use o nome oficial, em inglês, sem tradução no código.
4. **SDs só conversam por Service Operations.** Não pode haver banco de dados compartilhado nem leitura direta do estado de outro SD.
5. **O SD é neutro de canal e de tecnologia.** Fila, gateway, cache e dispatcher são infraestrutura, não Service Domains.
6. **Processo não é SD.** Um fluxo ponta a ponta é um **Business Scenario** que orquestra vários SDs. Quem conduz a orquestração costuma ser um SD de padrão Process/Fulfill (ex.: Customer Offer).
7. **Lacunas viram extensão documentada.** Se nenhum SD cobre a necessidade (ex.: regra local de regulador), registre uma extensão local com justificativa, prefixo próprio e SD BIAN mais próximo. Não renomeie um SD oficial.
8. **Fixe a versão.** Todo artefato informa a versão do BIAN usada (ex.: `BIAN v14.0`) e é revisado a cada release, verificando SDs obsoletos e renomeados.

---

## 4. Functional Patterns → Generic Artifacts (referência de trabalho)

| Functional Pattern | Generic Artifact (no nome do CR) | Uso típico |
|---|---|---|
| Direct | Strategy | Direção estratégica (Corporate Strategy) |
| Manage | Management Plan | Gestão de unidade ou portfólio |
| Administer | Administrative Plan | Administração de recursos |
| Design | Specification | Desenho de produto ou modelo |
| Develop | Development Project | Desenvolvimento de solução |
| Process | Procedure | Processamento orquestrado (Customer Offer) |
| Operate | Operating Session | Operação de equipamento ou canal |
| Maintain | Maintenance Agreement | Manutenção de ativo |
| Fulfill | Fulfillment Arrangement | Produtos contratados (Consumer Loan, Current Account) |
| Transact | Transaction | Transações (Payment Order Initiation, Card Authorization) |
| Agree Terms | Agreement | Acordos (Sales Product Agreement, Partner Agreement) |
| Advise | Advice | Assessoria (Consumer Advisory Services; reclassificados na v14) |
| Assess | Assessment | Avaliação (Underwriting, Customer Credit Rating) |
| Analyze | Analysis | Análise (Customer Behavior Insights) |
| Monitor | State / Measurement | Monitoramento contínuo |
| Track | Log | Rastreamento de eventos (Customer Event History) |
| Allocate | Allocation | Alocação (Collateral Allocation Management) |
| Enroll | Membership | Adesão a programa |
| Register / Catalog | Directory Entry | Diretórios (Product Directory, Party Reference Data Directory) |

O nome do CR é o Asset Type seguido do Generic Artifact, por exemplo *Consumer Loan* + *Fulfillment Arrangement*.

---

## 5. Action Terms e convenção das Semantic APIs

As Semantic APIs publicadas seguem o formato `/{ServiceDomain}/{crId}/{BehaviorQualifier}/{bqId}/{ActionTerm}`. Os nomes ficam em PascalCase e os identificadores em minúsculas (ex.: `{consumerloanid}`).

| Action Term | Intenção | Verbo HTTP usual | Exemplo |
|---|---|---|---|
| Initiate | Criar uma instância de CR (início do ciclo de vida) | POST | `POST /ConsumerLoan/Initiate` |
| Create / Register | Criar uma entrada (diretórios, cadastros) | POST | `POST /ProductDirectory/Register` |
| Evaluate / Provide | Solicitar avaliação ou provisão sem estado persistente do chamador | POST | `POST /CustomerCreditRating/Evaluate` |
| Update | Alterar dados do CR ou BQ | PUT | `PUT /ConsumerLoan/{id}/Update` |
| Configure | Alterar parâmetros de comportamento | PUT | `PUT /CurrentAccount/{id}/Configure` |
| Execute | Executar uma ação automatizada sobre o CR ou BQ | PUT | `PUT /ConsumerLoan/{id}/Disbursement/{did}/Execute` |
| Request | Pedir uma ação que pode exigir decisão ou trabalho manual | PUT | `PUT /ConsumerLoan/{id}/Request` |
| Control | Suspender, retomar ou encerrar o processamento | PUT | `PUT /ConsumerLoan/{id}/Control` |
| Exchange | Aceitar, rejeitar ou verificar (troca de estado com o chamador) | PUT | `PUT /CustomerOffer/{id}/Exchange` |
| Capture / Record | Registrar um fato ou dado externo | PUT | `PUT /ConsumerLoan/{id}/Capture` |
| Grant / Authorize | Conceder ou autorizar | PUT | `PUT /TransactionAuthorization/{id}/Grant` |
| Notify | Notificar um evento (natural em AsyncAPI) | POST | `POST /ConsumerLoan/{id}/Notify` |
| Retrieve | Consultar CR ou BQ | GET | `GET /ConsumerLoan/{id}/Retrieve` |
| Terminate / Activate / Feedback | Encerrar, ativar, retroalimentar | PUT | `PUT /ConsumerLoan/{id}/Terminate` |

**Alinhamento com a guideline de API da casa** (recomendação do projeto, não do BIAN):

- A Semantic API é a especificação **lógica**. A API física pode seguir o padrão REST da casa (skill `api-design-guidance`), desde que cada endpoint aponte para o SD e o Action Term correspondentes.
- No OpenAPI, registre o vínculo com extensões: `x-bian-version: "14.0"`, `x-bian-service-domain: "ConsumerLoan"`, `x-bian-control-record`, `x-bian-behavior-qualifier`, `x-bian-action-term: "Initiate"`.
- Payloads devem derivar do **BOM** ou do **ISO 20022** (pagamentos). Atributos próprios do banco entram como extensão nomeada, não como renomeação de atributo BIAN.

---

## 6. Do BIAN para o código (Hexagonal + DDD)

| Elemento BIAN | Elemento de implementação |
|---|---|
| Business Domain | Agrupamento de módulos, time ou *team topology* |
| Service Domain | **Bounded context**: um módulo ou serviço implantável, que pode reunir mais de um SD por decisão de custo |
| Control Record | **Aggregate root** do contexto |
| Behavior Qualifier | Entidade ou value object dentro do agregado, ou sub-recurso da API |
| Service Operation | **Caso de uso** (porta de entrada em `application`) |
| Service Exchange (saída) | **Porta de saída** e adapter (REST, mensageria) para outro SD |
| Notify / evento | **Domain event** publicado (AsyncAPI) |
| BOM / ISO 20022 | DTOs da borda e **Anti-Corruption Layer** com o legado |

Regras de implementação:

- Nome do pacote ou módulo a partir do SD: `consumerloan`, `customeroffer`, `partneragreement`.
- O domínio não importa DTOs BIAN. A tradução BOM ↔ domínio fica no adapter.
- Um serviço que agrega vários SDs mantém **pacotes separados por SD** e proíbe dependência direta entre agregados de SDs diferentes. Use ArchUnit e a skill `architecture-guidance`.
- Integração com legado: cada SD "envolvido" pelo legado ganha um adapter ACL, permitindo extrair um SD por vez.

---

## 7. Estrutura de saída exigida do agente

Toda resposta de modelagem BIAN termina com estas **cinco seções**:

### 1. Contexto e escopo
Evento ou capacidade de negócio, atores, versão do BIAN usada e fronteira do que está dentro e fora do escopo.

### 2. Mapeamento para o Service Landscape
Tabela com uma linha por SD envolvido:

| Business Area | Business Domain | Service Domain | Functional Pattern | Control Record | Papel no cenário | Componente atual / alvo |
|---|---|---|---|---|---|---|

### 3. Cenário de negócio (Service Exchanges)
Sequência das trocas entre SDs, cada uma com chamador → chamado, Action Term e propósito. Use um diagrama Mermaid `sequenceDiagram` com nomes de SD como participantes.

### 4. Contratos e implementação
Por SD: Service Operations e BQs usados, a Semantic API de referência, extensões `x-bian-*` e o mapeamento para bounded context, agregado e portas.

### 5. Lacunas, desvios e riscos
SDs ausentes no landscape (extensões locais), desvios conscientes do padrão com justificativa, SDs obsoletos ou renomeados na versão alvo, sobreposições de CR e riscos de acoplamento.

---

## 8. Exemplo: crédito consignado (mapeamento sugerido)

Exemplo para ilustrar o método. Valide com o portal antes de adotar.

| Necessidade de negócio | Service Domain (v14) | FP | Observação |
|---|---|---|---|
| Conduzir a proposta do pedido à contratação | **Customer Offer** | Process | Orquestra o cenário de originação |
| Contrato de empréstimo pessoal consignado | **Consumer Loan** | Fulfill | CR: Consumer Loan Fulfillment Arrangement; liberação via BQ de desembolso |
| Análise e decisão de crédito | **Underwriting**, **Customer Credit Rating** | Assess | Chamados por Customer Offer com `Evaluate` |
| Catálogo do produto e convênios | **Product Directory**, **Special Pricing Conditions** | Register / Agree Terms | Taxa e convênio como parâmetros |
| Correspondente bancário e fintech parceira | **Partner Agreement**, **Partner Management**, **Partner Administration** | Agree Terms / Manage | Partner Agreement substitui Product Broker Agreement na v14 |
| Comissão do correspondente | **Commission Agreement**, **Commissions** | Agree Terms / Transact | — |
| Consentimento do cliente (LGPD, Open Finance) | **Customer Consent** | Agree Terms | SD novo na v14 |
| Crédito do valor ao cliente | **Disbursement** | Transact | — |
| Prevenção a fraude | **Fraud Evaluation** | Assess | — |
| Histórico e status para o parceiro | **Customer Event History**, `Retrieve` em Consumer Loan | Track | — |
| **Averbação no órgão consignante** (INSS, eSocial) | *Lacuna*: não há SD específico | — | Extensão local ligada a Consumer Loan e Financial Gateway, documentada na seção 5 da saída |

Absorção de picos, idempotência, filas e dispatcher **não são Service Domains**. São mecanismos técnicos da implementação das Service Operations de Customer Offer e Consumer Loan.

---

## 9. Fontes oficiais

| Recurso | Endereço | Uso |
|---|---|---|
| Service Landscape 14.0 (portal HTML) | https://bian.org/servicelandscape-14-0-0/ | Value Chain View, Matrix View, cenários, wireframes, CR e BOM |
| Release Notes v14.0 | https://bian.org/wp-content/uploads/2026/02/BIAN-v14.0-Release-Notes-v1.0_-Final-Version.pdf | Mudanças da versão |
| Semantic APIs (GitHub) | https://github.com/bian-official/public | OAS 3 e AsyncAPI 3 com BOM e ISO 20022 (`release14.0.0/`) |
| Portal BIAN | https://portal.bian.org | Modelo e APIs (exige cadastro) |

Ao consultar um SD, comece pelo *Service Domain Overview Diagram* no portal e navegue pelas relações (CR, BQ, operações, cenários).

---

## 10. Checklist de conformidade e antipadrões

**Checklist:**

- [ ] A versão do BIAN está declarada no artefato.
- [ ] Todo componente de negócio aponta para um ou mais SDs oficiais, com nome exato.
- [ ] Cada SD controla um único CR, e nenhum CR é controlado por dois componentes.
- [ ] Nenhum SD obsoleto ou renomeado na versão alvo (veja o catálogo).
- [ ] As interações entre SDs passam por Service Operations, sem banco compartilhado.
- [ ] As APIs carregam `x-bian-*` e os payloads derivam do BOM ou do ISO 20022.
- [ ] As lacunas estão registradas como extensões locais, com justificativa.
- [ ] O cenário de negócio está documentado como sequência de Service Exchanges.

**Antipadrões:**

| Antipadrão | Por que é problema | Correção |
|---|---|---|
| "SD por sistema" (ex.: "SD do Core Banking") | Herda as fronteiras do legado | Mapear as capacidades do sistema para vários SDs |
| SD técnico (fila, cache, gateway de API) | Infraestrutura não é capacidade de negócio | Tratar como mecanismo da implementação |
| Processo como SD ("SD de Originação") | Duplica o que o cenário orquestra | Business Scenario + SD de padrão Process (Customer Offer) |
| Tradução ou renomeação dos SDs no código | Perde a rastreabilidade com o padrão | Nome oficial em inglês; tradução só na documentação |
| CRUD genérico no lugar de Action Terms | Esconde a intenção de negócio | Expor operações pelo Action Term (Initiate, Execute, Exchange...) |
| Microsserviço por SD sem critério | Custo operacional e latência | Agrupar SDs por Business Domain, mantendo pacotes separados |

---

## 11. Integração com outras skills

| Skill | Relação |
|---|---|
| `architecture-guidance` | SD = bounded context; CR = aggregate root; regras de dependência |
| `api-design-guidance` | API física e OpenAPI, com extensões `x-bian-*` |
| `service-modeling-skill` | Business Service e Service Offer (TOGAF) agrupam SDs; um SD não é uma oferta |
| `lgpd-sre-compliance-skill` | Dados pessoais nos payloads BOM (Party, Customer) |
| `resilience-checker-skill` | Service Exchanges síncronos entre SDs e com o legado |
| `tech-documentation-skill` | ADR para desvios do padrão e extensões locais |
