# Modelagem de Serviços de Negócio e Ofertas de Serviço (TAGF)

Modelagem de negócio da plataforma de crédito consignado, feita com a skill `service-modeling-skill`. Ela separa **o que** o banco entrega (Serviços de Negócio, estáveis) de **como** cada público consome essa entrega (Ofertas de Serviço, que mudam com mercado, campanha e regulação). Os números de SLA vêm das metas da PoC ([contexto e objetivos](contexto-e-objetivos.md)). Ainda não foram homologados e não são compromissos contratuais.

---

## 1. Contexto estratégico e objetivo de negócio

**Problema.** Na virada de folha (00:01, quando INSS, eSocial ou governos liberam a margem consignável), a demanda sobe de 50 para 10.000 propostas por segundo. O Core Banking aguenta 180. Na arquitetura síncrona, ele cai por 40 a 60 minutos, a margem do cliente vai para o concorrente e os retries dos robôs geram averbação duplicada, com risco judicial e regulatório (Autorregulação Febraban/ABBC, BCB, MTE).

**Beneficiários do valor.**

| Stakeholder | Valor esperado |
|---|---|
| Cliente final (aposentado, servidor, CLT) | ter o crédito contratado na janela, sem desconto duplicado em folha |
| Correspondente bancário e fintech parceira | ter a proposta aceita e rastreável, sem perder margem para rivais |
| Banco (negócio e diretoria comercial) | originar o máximo de VGV na janela mais rentável do mês |
| Compliance e jurídico | não ter averbação duplicada; ter trilha auditável |
| TI e operação | ter o legado protegido, sem acionamentos de madrugada |

**Objetivo.** Garantir a originação de consignado na janela de pico sem reescrever o Core Banking, dando à capacidade escassa o destino de maior valor.

---

## 2. Serviços de Negócio (Business Services)

A PoC não é, ela mesma, um Serviço de Negócio. API, filas, DynamoDB e worker são canais e recursos. Os serviços abaixo existiriam do mesmo jeito com atendimento em agência e formulário em papel.

### SN-01 Originar Crédito Consignado (serviço principal)

- **Valor gerado:** transforma a intenção de crédito do cliente, apresentada por um parceiro, em uma proposta formalmente recebida, única e com destino garantido (averbada ou recusada com motivo).
- **Estabilidade:** enquanto o produto consignado existir, o banco precisa receber propostas e decidir sobre elas. Mudam os convênios, as taxas e os canais; o serviço permanece.
- **Atores:** parceiro (solicitante), mesa de crédito e originação (responsável), Core Banking (executor da decisão).
- **Capacidades de suporte:** Gestão de Parceiros, Recepção de Propostas, Unicidade de Proposta (idempotência de negócio), Gestão de Capacidade de Originação.

### SN-02 Averbar Consignação

- **Valor gerado:** reserva a margem e registra o desconto em folha junto ao órgão consignante (INSS, eSocial/FGTS Digital, entes públicos), exatamente uma vez por proposta.
- **Estabilidade:** é obrigação regulatória do produto e independe de tecnologia.
- **Atores:** Core Banking, órgão consignante, backoffice de crédito.
- **Capacidades de suporte:** Integração com Consignantes, Conciliação de Averbações, Tratamento de Recusas.

### SN-03 Priorizar Originação por Valor

- **Valor gerado:** quando a capacidade é escassa, distribui o atendimento conforme o valor estratégico da operação: alto valor, seguro acoplado e portabilidade primeiro (política 70/30 da PoC).
- **Estabilidade:** a regra de priorização muda (é parâmetro de oferta). A necessidade de priorizar sob escassez, não.
- **Atores:** diretoria comercial (define a política), operação (monitora).
- **Capacidades de suporte:** Classificação de Propostas, Gestão de Política Comercial.

### SN-04 Informar Situação da Proposta

- **Valor gerado:** dá ao parceiro e ao cliente a certeza do estado da proposta (recebida, na fila, averbada, recusada). Isso evita reenvio e disputa.
- **Estabilidade:** transparência sobre o pedido é necessidade permanente, reforçada por Open Finance e pela Resolução Conjunta CMN/BCB nº 15/2025 na portabilidade.
- **Atores:** parceiro, atendimento, ouvidoria.
- **Capacidades de suporte:** Rastreabilidade de Propostas, Comunicação com Parceiros.

---

## 3. Ofertas de Serviço (Service Offers)

O relacionamento é 1-para-N: SN-01 sustenta todas as ofertas abaixo, e cada oferta varia canal, SLA e modelo comercial.

### OF-01 Originação Virada de Folha para Parceiros Digitais

- **Serviços vinculados:** SN-01, SN-02, SN-03, SN-04.
- **Público:** correspondentes e fintechs com integração automatizada (robôs).
- **Canais:** API REST autenticada por parceiro (tenant), com código único de proposta gerado pelo parceiro.
- **SLA e parâmetros (metas da PoC):** confirmação de recebimento (`202`) com p95 abaixo de 100 ms; 10.000 propostas/s de absorção agregada; reenvios da mesma proposta reconhecidos e descartados sem custo; envio ao Core Banking limitado a 180/s; tempo estimado de escoamento do backlog visível.
- **Modelo comercial:** remuneração do parceiro por comissão sobre proposta averbada. Duplicatas não geram comissão nem custo. Limites de vazão por parceiro podem compor contrato (ainda não definido).

### OF-02 Portabilidade Prioritária de Consignado

- **Serviços vinculados:** SN-01, SN-02, SN-03.
- **Público:** parceiros e clientes que trazem contrato de outro banco.
- **Canais:** os mesmos de OF-01, com a proposta marcada como portabilidade.
- **SLA e parâmetros:** entra na faixa prioritária da fila. O prazo de resposta ao banco de origem segue a regulação vigente (a confirmar com compliance).
- **Modelo comercial:** captura de carteira de concorrente. O custo de prioridade é aceito pelo ganho de participação de mercado.

### OF-03 Consignado com Seguro Acoplado de Alto Valor

- **Serviços vinculados:** SN-01, SN-02, SN-03.
- **Público:** operações acima do corte de valor definido pela diretoria comercial, ou com seguro prestamista.
- **Canais:** API de parceiros. No futuro, também os canais próprios do banco.
- **SLA e parâmetros:** faixa prioritária (70% da capacidade na política atual); corte de valor parametrizável por campanha.
- **Modelo comercial:** margem de crédito somada à receita de seguro; maior VGV por TPS consumido do legado.

### OF-04 Originação Padrão Fora de Pico (oferta candidata)

- **Serviços vinculados:** SN-01, SN-02, SN-04.
- **Público:** demanda regular do mês, de parceiros e canais próprios.
- **Canais:** API, portal do correspondente e app do banco (canais fora do escopo da PoC).
- **SLA e parâmetros:** sem fila prioritária; SLA de decisão mais longo e mais barato.
- **Modelo comercial:** custo operacional mínimo; serve de linha de base para comparar as ofertas de pico.

---

## 4. Relatório de obstáculos e riscos

| # | Obstáculo ou risco | Impacto | Mitigação / boa prática |
|---|---|---|---|
| 1 | **Confundir canal com serviço.** Chamar "API de propostas" ou "fila SQS" de Serviço de Negócio. | Catálogo amarrado à tecnologia; toda troca técnica vira mudança de negócio. | Manter endpoints, filas e worker como canais e recursos das ofertas. Revisar o catálogo pelo teste "existiria sem software?". |
| 2 | **Regra de priorização dentro do serviço.** Gravar 70/30 e corte de valor como parte de SN-03. | A regra passa a parecer estável e muda sem governança comercial. | 70/30 e cortes ficam como parâmetro de oferta, versionado e com dono na diretoria comercial. |
| 3 | **SLA de PoC tratado como contrato.** Publicar 100 ms / 10.000 req/s para parceiros. | Compromisso sem homologação (está pendente; veja [estado atual](../estado-atual.md)). | Rotular como meta até a homologação; definir SLO interno antes do SLA externo. |
| 4 | **"Recebido" lido como "aprovado".** O `202` confirma recebimento, não concessão. | Expectativa errada do cliente, reclamação e ouvidoria. | Dar a SN-04 estados claros e linguagem explícita na oferta; comunicar recusas com motivo. |
| 5 | **Justiça entre parceiros.** Robôs de grandes parceiros podem consumir a capacidade de absorção. | Risco concorrencial e reputacional; possível questionamento de tratamento não isonômico. | Limites por parceiro como parâmetro de oferta, com critério publicado e auditável. |
| 6 | **Priorização por valor e público vulnerável.** Aposentados de menor renda tendem a cair na faixa não prioritária. | Risco ético e regulatório (autorregulação, proteção ao consumidor). | Garantir capacidade mínima (os 30%) e prazo máximo de espera; revisar a política com compliance. |
| 7 | **Sobreposição entre SN-01 e SN-02.** Tratar originação e averbação como um único serviço. | Responsabilidade difusa quando o consignante recusa ou duplica. | Manter dono e indicador separados. A unicidade é garantida em SN-01, e a conciliação em SN-02. |
| 8 | **Dados pessoais nas ofertas.** CPF e valor circulam por canais de parceiro. | Exposição LGPD. | Minimização e mascaramento conforme a skill `lgpd-sre-compliance-skill`; contrato de tratamento com cada parceiro. |

**Antipadrões a evitar:** um serviço por endpoint; uma oferta sem público definido; SLA declarado no serviço; preço ou campanha embutidos no nome do Serviço de Negócio.

## Próximos passos sugeridos

1. Validar SN-01 a SN-04 e seus donos com originação, comercial e compliance.
2. Definir os parâmetros de OF-01 a OF-03 que viram contrato (limites por parceiro, prazos de portabilidade).
3. Ligar cada oferta aos SLIs já existentes em [docs/sre](../sre/README.md), para que o SLA seja medido pelo mesmo indicador.
