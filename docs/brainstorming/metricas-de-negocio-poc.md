# Painel Executivo de Métricas de Negócio — PoC Crédito Consignado

**Projeto:** PoC de Ingestão Massiva e Resiliência — Crédito Consignado  
**Público-Alvo:** C-Level, Diretores de Negócio, Crédito e Engenharia de Software  
**Objetivo:** Monitorar a preservação de receita, eficiência de aquisição, rentabilidade e governança durante o pico da "virada de folha" (10.000 TPS).

---

## 1. Métricas de Volume e Faturamento (Originação de Crédito)

| Métrica | Descrição | Impacto no Negócio |
| :--- | :--- | :--- |
| **VGV Aprovado e Averbado** | Volume Financeiro Total (R$) de propostas aceitas e averbadas nos órgãos pagadores, segregado por convênio (INSS, Servidor Público, CLT/Crédito do Trabalhador). | Mede a geração bruta de nova carteira de crédito. |
| **Ticket Médio por Operação** | Valor médio contratado por proposta aprovada (compara bancões ~R$ 5,8k vs. fintechs ~R$ 3,6k). | Avalia a eficiência de captura por perfil de cliente e canal. |
| **VGV Preservado na Virada (SLA 5 min)** | Volume financeiro (R$) "carimbado" e garantido na camada de ingestão nos primeiros minutos após as 00:01 (a 10.000 TPS). | Quantifica o faturamento protegido contra quedas do sistema que alimentariam a concorrência. |
| **Taxa de Conversão por Canal / Parceiro** | Percentual de propostas iniciadas por robôs de correspondentes que resultam em averbação efetiva no órgão pagador. | Identifica os parceiros mais eficientes e reduz custos de processamento inútil. |

---

## 2. Métricas de Lucratividade, Qualidade de Carteira e QoS

| Métrica | Descrição | Impacto no Negócio |
| :--- | :--- | :--- |
| **Orçamento de QoS (Fast vs. Standard)** | Proporção de VGV processado na **Fast Lane SQS** (70% do orçamento do legado / 126 TPS) vs. **Standard Lane SQS** (30% / 54 TPS). | Garante prioridade de processamento para clientes de alto LTV, seguros acoplados e maior rentabilidade. |
| **Volume de Portabilidade (Open Finance)** | Saldo devedor e quantidade de operações capturadas de concorrentes ou retidas via portabilidade eletrônica (Resoluções CMN nº 4.292/2013 e nº 15/2025). | Mede a expansão da carteira sobre os concorrentes e o cumprimento de prazos regulatórios do BCB. |
| **Spread Médio Financeiro (CET)** | Acompanhamento da taxa média praticada em relação aos tetos regulatórios (ex: INSS e Crédito do Trabalhador). | Protege a margem financeira líquida da operação de crédito. |

---

## 3. Métricas de Eficiência Operacional e Liquidação do Backlog

| Métrica | Descrição | Impacto no Negócio |
| :--- | :--- | :--- |
| **Net Backlog Growth ($\lambda - \mu$)** | Diferença em tempo real entre a taxa de chegada na borda ($\lambda$) e a taxa de processamento no legado ($\mu$). | Indica visualmente a velocidade de acúmulo da fila de espera durante o pico de tráfego. |
| **Effective Drain Time (Tempo de Recuperação)** | Indicador preditivo calculado por: $\frac{\text{Volume Atual no SQS}}{\text{Vazão Estável do Legado (180 TPS)}}$. | Informa à diretoria o tempo exato estimado para liquidar 100% do backlog acumulado na virada. |
| **Taxa de Averbação de Primeira Passagem** | Percentual de propostas aprovadas de primeira sem rejeição por margem estourada ou inconsistência de biometria facial. | Reduz o custo operacional de reprocessamento e atrito com o cliente. |

---

## 4. Métricas de FinOps, Risco e Governança (Compliance)

| Métrica | Descrição | Impacto no Negócio |
| :--- | :--- | :--- |
| **Taxa de Rejeição por Idempotência** | Quantidade e % de requisições duplicadas enviadas por robôs descartadas atômica e gratuitamente no DynamoDB (`attribute_not_exists`). | Elimina o risco de *race conditions* (dupla averbação e pagamento em duplicidade) e reduz custos com infraestrutura de nuvem. |
| **Throttling e Bloqueio Anti-Spam por Tenant** | Métrica de bloqueios na borda (HTTP 429) por correspondente/parceiro tentando uso abusivo de robôs. | Comprova alinhamento com as regras da Autorregulação do Consignado (Febraban/ABBC - mais de 2.200 sanções aplicadas). |

---

## 5. Visualização Recomendada no Grafana / Datadog

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│ PAINEL SUPERIOR: MÉTRICAS DE NEGÓCIO E VGV (VISÃO C-LEVEL)                                │
│ ┌──────────────────────┐ ┌──────────────────────┐ ┌──────────────────────┐ ┌─────────────┐ │
│ │ VGV Preservado (Virada)│ │ VGV Total Averbado   │ │ Effective Drain Time │ │ Fast Lane % │ │
│ │ R$ 420.500.000,00    │ │ R$ 1.120.000.000,00  │ │ 00h 14m 32s          │ │ 69.8% / 70% │ │
│ └──────────────────────┘ └──────────────────────┘ └──────────────────────┘ └─────────────┘ │
├───────────────────────────────────────────────────────────────────────────────────────────┤
│ PAINEL INFERIOR: PROTEÇÃO DO LEGADO E FINOPS (VISÃO ENGENHARIA & RISCO)                   │
│ ┌──────────────────────────────────────────────┐ ┌──────────────────────────────────────┐ │
│ │ Taxa Ingestão (Borda): 10.000 TPS (202 OK)    │ │ Taxa Legado: 180 TPS (Linha Reta)    │ │
│ │ Rejeições Idempotência: 98,2% (Filtro Robôs)  │ │ Throttling Multi-Tenant: 12 HTTP 429 │ │
│ └──────────────────────────────────────────────┘ └──────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```
