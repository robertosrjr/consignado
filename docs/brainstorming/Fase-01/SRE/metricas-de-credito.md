# Métricas de Crédito Consignado

Para o mercado de crédito consignado — caracterizado por **picos de volumetria extrema na "virada de folha"**, margens operacionais reguladas e forte dependência de órgãos governamentais (Dataprev, eConsig, eSocial, SouGov) — as métricas de negócio devem conectar o desempenho comercial ao comportamento dos sistemas.

Abaixo estão as **principais métricas de negócio** agrupadas pelo ciclo de vida da operação:

---

## 1. Métricas de Originação e Volume Financeiro (VGV)
* **VGV Recebido (Valor Global de Vendas na Borda):** Soma do valor bruto financiado de todas as propostas novas capturadas e aceitas na camada de ingestão.
* **VGV Confirmado / Averbado:** Soma do valor líquido das propostas efetivamente registradas e averbadas nos sistemas pagadores (INSS, MTE/eSocial, Governo Federal).
* **Divergência de VGV (Gargalo de Represamento):** A diferença entre o VGV Recebido e o VGV Confirmado em uma mesma janela de tempo. Se essa lacuna cresce durante a virada de folha, indica que propostas estão represadas em filas enquanto o banco concorrente toma a margem do cliente.
* **Ticket Médio por Convênio:** Valor médio financiado por operação, segmentado entre **INSS**, **Servidores Públicos (SIAPE/SouGov)** e **CLT / Crédito do Trabalhador (eSocial/FGTS Digital)**.

---

## 2. Métricas de Eficiência e Funil de Averbação
* **Taxa de Conversão / Aprovados de Primeira (*First-Pass Approval Rate*):** Percentual de contratos confirmados na primeira tentativa junto ao órgão pagador, sem necessidade de reprocessamento ou reconciliação.
* **Taxa de Rejeição por Regra de Margem / Elegibilidade:** Percentual de propostas recusadas por estouro do limite de **45% de margem consignável** (35% empréstimo pessoal, 5% cartão RMC e 5% cartão benefício RCC) ou descumprimento da **trava DDB de 90 dias** do INSS.
* **Volume de Portabilidade Capturada (Open Finance):** Montante e quantidade de operações capturadas da concorrência via compartilhamento de dados e portabilidade automatizada do Banco Central.

---

## 3. Métricas de Desempenho Operacional e SLAs de Negócio
* **Tempo Até a Efetivação (*Time-to-Settle*):** Duração média entre o aceite formal da proposta na API e o recebimento do código de averbação oficial do órgão.
* **Performance por Faixa de Prioridade (QoS Lanes):** Proporção e tempo de liquidação de operações na **Fast Lane** (alto LTV ou risco de concorrência) versus a **Standard Lane**.

---

## 4. Métricas de Proteção e Eficiência de Canal (FinOps & Parceiros)
* **Taxa de Rejeição de Duplicidade na Borda (*Business Fingerprint*):** Volume de chamadas redundantes disparadas por robôs de correspondentes bancários ("pastinhas") bloqueadas atômica na entrada antes de consumir o orçamento do *Core Banking*.
* **Índice de Qualidade do Parceiro (Autorregulação Febraban/ABBC):** Monitoramento do volume de propostas inválidas ou abusivas por correspondente para aplicação preventiva de bloqueios temporários.
