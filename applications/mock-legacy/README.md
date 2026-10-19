# mock-legacy

Core Banking simulado. Existe para a PoC ter contra o que provar a proteção do legado: ele **recusa** o que passa da capacidade.

## Por que um mock e não um stub simples

Um stub que sempre responde `200` provaria nada. A tese da PoC é que o Worker nunca ultrapassa 180 chamadas por segundo. Para isso, o legado precisa ser capaz de acusar a violação — e é o que o `CapacityGate` faz: acima do limite na janela de 1 segundo, responde `503` com `Retry-After`.

Se o teste de carga terminar sem nenhum `503` por capacidade, a proteção funcionou. Se houver, o orçamento de vazão falhou. O mock é o juiz do experimento.

## Comportamento

| Recurso | Como funciona |
|---|---|
| Capacidade | janela deslizante de 1 s; acima de `LEGACY_CAPACITY_TPS` responde `503` |
| Idempotência | mesma `Idempotency-Key` devolve o mesmo resultado, com header `X-Replayed` |
| Caos | latência fixa e taxa de erro configuráveis, para exercitar o circuit breaker do Worker |
| Pico observado | `legacy_peak_window` guarda a maior janela já vista |

O `legacy_peak_window` importa porque o Prometheus raspa a cada 15 s: um pico de 1 s passaria despercebido em um gauge instantâneo. Esse medidor retém o pior caso.

## API

`POST /v1/averbacoes`, autenticada por `X-Api-Key`.

| Status | Quando |
|---|---|
| `200` | averbação registrada ou repetida |
| `400` | payload ou `Idempotency-Key` inválidos |
| `401` | chave de API ausente ou inválida |
| `503` | capacidade excedida ou falha simulada |

## Configuração

Para falhas temporárias sem reiniciar o mock, consultar a [preparação SRE do legado](../../docs/sre/preparacao-falhas-legado.md). O controle é desligado por padrão e exige perfil de experimento, flag explícita, token próprio e acesso loopback.

| Variável | Padrão | Para quê |
|---|---|---|
| `LEGACY_API_KEY` | — (obrigatória) | o serviço não sobe sem ela |
| `LEGACY_CAPACITY_TPS` | 180 | capacidade por janela de 1 s |
| `LEGACY_CHAOS_LATENCY_MS` | 0 | latência artificial |
| `LEGACY_CHAOS_ERROR_RATE` | 0.0 | fração de falhas simuladas |
| `LEGACY_API_DOCS_ENABLED` | false | liga `/swagger-ui.html` |

## Como rodar

```bash
LEGACY_API_KEY=chave-de-teste SERVER_PORT=8081 mvn -pl mock-legacy spring-boot:run
```

Sobe em cerca de 7 segundos. Confirme com `GET /actuator/health`.

## Métricas que provam a tese

| Métrica | Leitura |
|---|---|
| `legacy_peak_window` | **deve ficar ≤ 180**; acima disso, a proteção falhou |
| `legacy_requests_total{outcome}` | `accepted`, `rejected_capacity`, `failed_chaos` |
| `legacy_averbacoes_total{kind}` | `duplicate` **deve ser zero**: é a prova de que não houve dupla averbação |

## Testes

```bash
mvn -pl mock-legacy test
```
