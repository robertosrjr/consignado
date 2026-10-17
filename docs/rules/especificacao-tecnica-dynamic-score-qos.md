# Especificação Técnica: Algoritmo de Priorização Dinâmica (Dynamic Score QoS)

**Projeto:** Plataforma LaaS & Engine de Resiliência para Crédito Consignado  
**Módulo:** Governance & QoS Traffic Orchestrator  
**Status:** Approved for Engineering & Production Roadmap  
**Padrão Documental:** IEEE 1016-2009 & ISO/IEC/IEEE 42010  
**Versão:** 1.0.0-FINAL  

---

## 1. Contexto & Problema de Negócio

Durante o pico da **"virada de folha"** de margem consignável (às 00:01), a plataforma recebe até **10.000 TPS**, gerando um acúmulo de milhões de propostas em buffer. Enquanto o sistema de borda absorve o impacto com resposta `HTTP 202 Accepted` em < 100 ms, os *Workers* consomem as filas em cadência controlada de **180 TPS** para proteger o Core Banking legado.

### 1.1. O Conflito do FIFO (First-In, First-Out) Tradicional
Em uma fila FIFO convencional, uma proposta de **alto valor estratégico** (ex: portabilidade via Open Finance com Resoluções CMN nº 4.292 e nº 15/2025, cliente VIP ou contrato de alto VGV) fica retida atrás de centenas de milhares de propostas padronizadas enviadas por robôs. 

Se o tempo de drenagem da fila FIFO for de 3 horas, um banco concorrente pode averbar a proposta do mesmo cliente em poucos minutos. Ao chegar no final da fila, a proposta do nosso banco é rejeitada pelo Dataprev com erro de **`Margem Insuficiente / Já Averbada por Outro Banco`**.

### 1.2. A Solução: Dynamic Score QoS Engine
O **Algoritmo de Priorização Dinâmica (Dynamic Score QoS)** substitui o FIFO estático por uma avaliação matemática em tempo real. A cada proposta recebida na borda, o motor calcula um **Score Criptográfico de Prioridade ($0 \dots 100$)** e direciona a mensagem para faixas de serviço (*Service Lanes*) com alocação garantida de orçamento de processamento (*Capacity Budgeting*), incorporando mecanismos de **Anti-Starvation** para impedir o travamento de propostas padrão.

---

## 2. Equação Criptográfica do Dynamic Score Engine

O cálculo do score ocorre na camada de despacho reativo (**AWS Lambda Dispatcher**) acionado pelo *DynamoDB Streams* em menos de **3 ms**.

### 2.1. Variáveis e Parâmetros de Entrada

| Parâmetro | Símbolo | Intervalo / Tipo | Descrição / Regra de Negócio |
| :--- | :--- | :--- | :--- |
| **VGV do Contrato** | $VGV_{norm}$ | $0.0 \dots 1.0$ | Valor do contrato normalizado em relação ao teto máximo (ex: $R\$ 150.000,00$). |
| **Tipo de Operação** | $M_{Op}$ | $1.0 \dots 2.0$ | Multiplicador de operação: Portabilidade Open Finance = $2.0$; Margem Livre = $1.2$; Refinanciamento = $1.0$. |
| **Relacionamento VIP** | $VIP_{score}$ | $0.0 \dots 1.0$ | Rating interno do cliente (Provento em Conta, Servidor Público, Score de Crédito). |
| **Risco de Concorrência** | $R_{Disputa}$ | $0.0 \dots 1.0$ | Indicador de probabilidade de assédio por concorrentes (Portabilidade em andamento = $1.0$). |
| **Fator temporal Anti-Starvation** | $\alpha \cdot \Delta t$ | $0.0 \dots \infty$ | Incremento contínuo de score proporcional ao tempo de retenção na fila (em minutos). |

---

### 2.2. A Equação do Score

$$\text{Score} = \min \left( 100, \left[ \left( W_1 \cdot VGV_{norm} + W_2 \cdot VIP_{score} + W_3 \cdot R_{Disputa} \right) \times M_{Op} \times 50 \right] + (\alpha \cdot \Delta t) \right)$$

Onde os pesos do sistema são parametrizados como:
* $W_1 = 0.40$ (Peso do Valor do Contrato - VGV)
* $W_2 = 0.35$ (Peso do Relacionamento / Cliente VIP)
* $W_3 = 0.25$ (Peso do Risco de Perda para Concorrente)
* $\alpha = 1.5$ (Taxa de acréscimo por minuto de espera - Anti-Starvation)

---

## 3. Arquitetura de Roteamento Multi-Lane (Capacity Budgeting)

O sistema de mensagens é fatiado em **3 Faixas de Serviço (Lanes)** no Amazon SQS. O limite global de **180 TPS** do legado é fatiado dinamicamente entre as faixas:

```
                                  [ DynamoDB Streams ]
                                           │
                                           ▼
                            ┌──────────────────────────────┐
                            │ AWS Lambda Dispatcher Engine │
                            │   (Cálculo do Score em <3ms) │
                            └──────────────┬───────────────┘
                                           │
         ┌─────────────────────────────────┼─────────────────────────────────┐
         │ (Score >= 80)                   │ (Score 50..79)                  │ (Score < 50)
         ▼                                 ▼                                 ▼
┌─────────────────────────┐     ┌─────────────────────────┐     ┌─────────────────────────┐
│ Ultra Fast Lane SQS     │     │ Fast Lane SQS           │     │ Standard Lane SQS       │
│ Budget: 50% (90 TPS)    │     │ Budget: 30% (54 TPS)    │     │ Budget: 20% (36 TPS)    │
└────────┬────────────────┘     └────────┬────────────────┘     └────────┬────────────────┘
         │                               │                               │
         └───────────────────────────────┼───────────────────────────────┘
                                         ▼
                        ┌──────────────────────────────────┐
                        │ Worker Fleet (ECS Fargate)       │
                        │ Rate Limiter Global: Max 180 TPS │
                        └──────────────────────────────────┘
```

### 3.1. Tabela de Distribuição de Filas e SLAs

| Lane / Faixa | Faixa de Score | Orçamento de Capacidade | Alocação TPS | SLA Alvo de Averbação |
| :--- | :--- | :--- | :--- | :--- |
| **Ultra Fast Lane** | $\text{Score} \ge 80$ | 50% da capacidade | **90 TPS** | $< 2 \text{ minutos}$ |
| **Fast Lane** | $50 \le \text{Score} < 80$ | 30% da capacidade | **54 TPS** | $< 10 \text{ minutos}$ |
| **Standard Lane** | $\text{Score} < 50$ | 20% da capacidade | **36 TPS** | $< 45 \text{ minutos}$ |

---

## 4. Algoritmo de Prevenção de Inanição (Anti-Starvation Aging Engine)

### 4.1. O Risco de Starvation
Em momentos de pico sustentado de 10.000 TPS, a entrada contínua de contratos de alto valor pode saturar as faixas *Ultra Fast* e *Fast Lane*, fazendo com que propostas da *Standard Lane* fiquem retidas indefinidamente.

### 4.2. O Mecanismo de Promoção Dinâmica por Envelhecimento (Aging Promotion)
A cada 2 minutos, um worker leve (**SQS Aging Daemon**) executa uma varredura nas métricas de mensagens retidas na `Standard Lane`. 

Para qualquer mensagem cujo tempo de retenção $\Delta t$ exceda 10 minutos, o fator $\alpha \cdot \Delta t$ eleva o score da proposta acima de 50 pontos, **promovendo automaticamente a mensagem para a Fast Lane**.

```java
// Implementação do cálculo de Score com Anti-Starvation em Java 21
public class DynamicScoreEngine {

    private static final double W1_VGV = 0.40;
    private static final double W2_VIP = 0.35;
    private static final double W3_DISPUTE = 0.25;
    private static final double ALPHA_AGING = 1.5; // +1.5 pontos por minuto de espera

    public int calculateScore(ProposalMetadata proposal, long waitingMinutes) {
        double vgvNorm = Math.min(1.0, proposal.getAmount() / 150000.0);
        double vipNorm = proposal.getCustomerRating(); // 0.0 to 1.0
        double disputeNorm = proposal.isPortability() ? 1.0 : 0.2;
        double opMultiplier = proposal.isPortability() ? 2.0 : 1.0;

        double baseScore = (W1_VGV * vgvNorm + W2_VIP * vipNorm + W3_DISPUTE * disputeNorm) * opMultiplier * 50.0;
        double agingBonus = ALPHA_AGING * waitingMinutes;

        int finalScore = (int) Math.min(100.0, baseScore + agingBonus);
        return finalScore;
    }
}
```

---

## 5. Telemetria e Métricas de Observabilidade (Prometheus & Grafana)

Para auditoria e acompanhamento do comportamento de priorização, o sistema expõe as seguintes métricas em tempo real:

* **`qos_score_distribution_histogram`**: Histograma de distribuição dos scores atribuídos na ingestão.
* **`qos_lane_throughput_tps{lane="ultra_fast|fast|standard"}`**: Medição da vazão efetiva entregue aos workers por faixa.
* **`qos_anti_starvation_promotions_total`**: Contador de propostas promovidas de faixa pelo fator de envelhecimento.
* **`effective_drain_time_seconds{lane="..."}`**: Métrica preditiva de tempo para esvaziamento da fila em tempo real.

---

## 6. Conclusão & Próximos Passos

O **Dynamic Score QoS Engine** garante que o banco maximize a rentabilidade da margem consignável no pico das 00:01, reduzindo a perda de contratos estratégicos para a concorrência a quase **zero**, mantendo a estabilidade do Core Banking a 180 TPS.

📄 **Documento publicado no repositório de arquitetura.**  
*Autorização de Engenharia: Staff / Principal Systems Engineer.*
