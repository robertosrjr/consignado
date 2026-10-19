# Especificacao Tecnica: Feedback de SLA Preditivo & Engine de Drain Time

**Projeto:** Platform Engine — Credito Consignado & LaaS  
**Documento:** Apendice Tecnico de Arquitetura  
**Modulo:** Predictive SLA & Real-Time Drain Time Engine  
**Maturidade Arquitetural:** Staff / Principal Systems Engineer  
**Status:** Approved for Implementation  
**Padrao Documental:** IEEE 1016-2009 / ISO 42010  

---

## 1. Controle do Documento & Resumo Executivo

### 1.1. Objetivo
Este documento especifica a arquitetura, algoritmos e contratos de API do **Engine de Feedback de SLA Preditivo (Predictive SLA & Drain Time Engine)**. O componente calcula dinamicamente, em milissegundos na borda da ingestao (**10.000 TPS**), o tempo estimado para a conclusao da averbacao da proposta junto aos orgaos pagadores (Dataprev, eSocial, SouGov) e ao Core Banking.

### 1.2. Problema de Negocio: Prevencao da "Guerra da Margem" e Retentativas
Durante a "virada de folha" de margem consignavel, correspondentes bancarios e robos automatizados sofrem de uma assimetria severa de informacao. Ao receberem uma resposta generica `HTTP 202 Accepted` sem estimativa de conclusao:
1. **Entram em Panico de Rede (*Retry Storms*)**: Retransmitem a mesma proposta centenas de vezes tentando obter confirmacao.
2. **Guerra da Margem (Perda para Concorrentes)**: Disparam o mesmo contrato para bancos concorrentes acreditando que a proposta original travou na fila.

Ao fornecer uma estimativa matematicamente precisa (`estimatedDrainTimeSeconds`) e um canal de notificacao reativa no momento do recebimento, o banco desincentiva a duplicidade e retem a exclusividade da proposta.

---

## 2. Algoritmo de Calculo do SLA Preditivo (Effective Drain Time)

O tempo estimado de liquidacao/averbacao é calculado em tempo real pelo **AWS Lambda Dispatcher** ou na **Ingestion API** a partir da profundidade instantanea da fila e da taxa efetiva de drenagem dos Workers.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      FORMULA DO DRAIN TIME PREDITIVO                    │
│                                                                         │
│                         Q_lane(t) + P_relative                          │
│   T_drain(t) = ────────────────────────────────────────── + Delta_ext   │
│                            mu_lane_eff(t)                               │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.1. Variaveis do Modelo
* **Q_lane(t)**: Quantidade total de mensagens pendentes no backlog da fila SQS especifica da faixa da proposta (Ultra Fast, Fast ou Standard) no instante t.
* **P_relative**: Posicao estimada da proposta no backlog, calculada com base na pontuacao atribuida pelo **Dynamic Score QoS Engine**.
* **mu_lane_eff(t)**: Taxa de processamento real e instantanea dos Workers para aquela faixa (ex: **90 TPS** para Ultra Fast, **54 TPS** para Fast, **36 TPS** para Standard).
* **Delta_ext**: Latencia media observada das chamadas aos gateways externos do governo (Dataprev/eSocial) nos ultimos 5 minutos (histograma p50 em tempo real).

---

## 3. Contratos de API & Payloads de Resposta (HTTP 202 Accepted)

A Ingestion API (`POST /v1/proposals`) injeta os metadados do SLA Preditivo diretamente nos cabecalhos de resposta HTTP e no corpo do payload JSON.

### 3.1. Cabecalhos HTTP de Resposta
* `X-Estimated-Drain-Time-Sec`: `185` *(Tempo estimado em segundos)*
* `X-Queue-Position-Estimate`: `4200` *(Posicao estimada no backlog)*
* `X-QoS-Lane-Assigned`: `ULTRA_FAST_LANE`

### 3.2. Payload JSON de Resposta (`HTTP 202 Accepted`)

```json
{
  "proposalId": "HASH#a3f8c9d2e1b4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0",
  "status": "ACCEPTED",
  "receivedAt": "2026-10-14T07:15:00.123Z",
  "slaPredictive": {
    "assignedLane": "ULTRA_FAST_LANE",
    "qosScore": 87.5,
    "estimatedDrainTimeSeconds": 185,
    "estimatedCompletionTime": "2026-10-14T07:18:05.123Z",
    "queueDepthAtIngestion": 14200,
    "confidenceInterval": "P95_98_PERCENT"
  },
  "realTimeFeedback": {
    "webhookCallbackRegistered": true
  }
}
```

---

## 4. Arquitetura do Loop de Feedback em Tempo Real

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    ARQUITETURA DE FEEDBACK EM TEMPO REAL                │
│                                                                         │
│   [ Parceiro / App ] ──► (HTTP 202 com SLA) ──► Ingestion API (Java 21) │
│          ▲                                                 │            │
│          │                                                 ▼            │
│   (Notificacao Reativa)                           DynamoDB / SQS        │
│          │                                                 │            │
│  ┌───────┴────────────────────────┐                        ▼            │
│  │ EventBridge / Webhook Engine   │ ◄── Outbox Log ── Worker Fleet      │
│  │ (Push status em < 500 ms)      │                   (180 TPS)         │
│  └────────────────────────────────┘                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.1. Modos de Acompanhamento pelo Parceiro
1. **Push Notifier (Webhook Proativo)**: O parceiro registra uma URL de callback no Estagio 1. O `Settlement-Engine` dispara eventos HTTP POST na mudanca de estado (`PROCESSING_STARTED`, `AVERBATED`, `REJECTED_GOV`).
2. **Server-Sent Events (SSE) / WebSocket**: O correspondente estabelece uma conexao leve em background para receber atualizacoes do ponteiro de posicao na fila sem polling exaustivo.
3. **Polling Otimizado com HTTP Conditional Get**: O endpoint `/v1/proposals/{id}/status` suporta cabecalhos `ETag` e `Cache-Control: max-age=5`. Se a posicao da proposta nao mudou significativamente, retorna `HTTP 304 Not Modified` em < 3 ms.

---

## 5. Implementacao do Calculo de SLA Preditivo em Java 21

```java
package br.com.banco.consignado.sla;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;

@Service
public class PredictiveSlaCalculatorService {

    private static final Map<String, Integer> LANE_EFFECTIVE_TPS = Map.of(
        "ULTRA_FAST_LANE", 90,
        "FAST_LANE", 54,
        "STANDARD_LANE", 36
    );

    private final RedisMetricsRepository redisMetrics;

    public PredictiveSlaCalculatorService(RedisMetricsRepository redisMetrics) {
        this.redisMetrics = redisMetrics;
    }

    public SlaPredictiveResult calculatePredictiveSla(String assignedLane, double qosScore) {
        long currentQueueDepth = redisMetrics.getQueueDepth(assignedLane);
        double externalGovLatencySec = redisMetrics.getGovLatencyP50Seconds();
        int laneTps = LANE_EFFECTIVE_TPS.getOrDefault(assignedLane, 36);

        // Ajuste relativo de posicao com base no Score de QoS
        double relativeFactor = Math.max(0.1, (100.0 - qosScore) / 100.0);
        long estimatedPosition = Math.round(currentQueueDepth * relativeFactor);

        long drainTimeSeconds = Math.round((double) estimatedPosition / laneTps) + Math.round(externalGovLatencySec);
        Instant estimatedCompletionTime = Instant.now().plusSeconds(drainTimeSeconds);

        return new SlaPredictiveResult(
            assignedLane,
            qosScore,
            drainTimeSeconds,
            estimatedCompletionTime,
            estimatedPosition
        );
    }
}
```

---

## 6. Telemetria SRE, Metricas Prometheus & Alertas Grafana

Para monitorar a acuracia da estimativa de SLA em comparacao com o tempo real de averbacao, os Workers registram a metrica de desvio do modelo:

### 6.1. Metricas Customizadas
* **`sla_prediction_error_seconds`**: Histograma da diferenca entre a previsao inicial e o tempo real de conclusao.
* **`effective_drain_time_seconds`**: Gauge com o tempo estimado de esvaziamento de cada fila SQS exposto no Grafana.
* **`webhook_notification_delivery_success_rate`**: Taxa de sucesso no envio de notificacoes de progresso aos parceiros.

---

📄 **Especificacao tecnica aprovada para integracao a arquitetura de producao.**  
*Referencia do Apendice: SLA-PRED-ENG-2026-V1*
