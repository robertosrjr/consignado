# SLOs do Consignado — v1 (contínuo)

**Limites da medição atual:** J1 usa métricas HTTP e exclui todo 429; ainda não distingue recusa indevida nem comprova sozinho 202 sem Inbox. J2 cobre itens roteados com `sloOpen`, não itens antigos ou nunca roteados. Esses limites não alteram as metas. As recording rules usam counters do avaliador e não constituem um histórico durável completo de 30 dias. Ver [validação S2](../../aidlc-docs/construction/sre-s2/validation.md) e [estado atual](../estado-atual.md).

**Versão:** `slo-v1`. **Estado:** aprovado na S1; medição implementada na S2 e alertas na S3 do [plano de evolução](../../aidlc-docs/inception/sre-evolucao/plano-evolucao-sre.md). Data: 23/10/2026.

Metas internas de engenharia, medidas em ambiente local. Não são SLA externo nem homologação de capacidade. O [contrato de medição](contrato-medicao.md) continua válido para experimentos com coorte finita; este documento define a medição **contínua** em janela móvel, implementada na S2, com os limites abaixo.

## Jornadas e observação

| Jornada | Resultado para o usuário | Ponto de observação |
|---|---|---|
| **J1 — aceite durável** | A proposta enviada é registrada de forma durável e respondida corretamente. | Servidor da `ingestion-api`, `POST /v1/proposals` |
| **J2 — decisão no prazo** | Cada proposta aceita recebe decisão correta (confirmada ou rejeição comercial) até o prazo da sua faixa. | Avaliador de prazos sobre a Inbox, por proposta canônica |

Invariante fora do orçamento: **zero perda e zero duplicidade financeira**. Uma única ocorrência é incidente, independentemente do budget restante.

## J1 — aceite durável

| Item | Definição |
|---|---|
| População elegível | `POST /v1/proposals` com credencial válida, payload conforme o contrato e dentro da cota |
| Exclusões | 4xx de validação (400/422), credencial inválida (401/403) e 429 de cota excedida pelo cliente. Um 429 a cliente dentro da cota é evento ruim. |
| Evento bom (disponibilidade) | 202 com `RECEIVED` ou `DUPLICATE_ACCEPTED` |
| Evento ruim | 5xx, timeout e 202 sem registro na Inbox (verificado pela conciliação periódica) |
| **SLO-J1-A** | **99,5%** dos eventos elegíveis bons, janela móvel de 30 dias |
| **SLO-J1-L** | 95% dos aceites bons em ≤ 100 ms e 99% em ≤ 200 ms, mesma janela |
| Error budget | 0,5% dos eventos elegíveis (A); 5% e 1% (L) |

Dados ausentes: sem amostra recente (`push_time_seconds` com mais de 120 s), a janela fica **sem dado**. Isso não conta como sucesso nem como falha e é sinalizado pelo alerta de frescor.

## J2 — decisão no prazo

Prazo por proposta: `deadline = aceite original + prazo da faixa`. Replays não criam proposta nova nem prazo novo.

| Faixa | **SLO** | Prazo |
|---|---|---|
| fast | **95%** das propostas com decisão correta até o prazo | 2 min |
| standard | **90%** das propostas com decisão correta até o prazo | 45 min |
| unrouted | 0 propostas sem faixa após 5 min (controle, sem budget) | — |

- **Denominador:** propostas da faixa cujo prazo venceu dentro da janela móvel de 30 dias. Propostas ainda dentro do prazo ficam em `notYetDue`, fora do cálculo.
- **Bom:** `confirmed` ou `business_rejected` fundamentado até o prazo. Uma decisão no instante exato do prazo é boa.
- **Ruim:** decisão tardia, `technical_failure`, ausência de decisão após o prazo, mensagem na DLQ. Uma decisão correta tardia resolve a pendência, mas continua ruim para o prazo.
- **Error budget:** 5% (fast) e 10% (standard) das propostas vencidas.

### Restrição física

O legado admite cerca de **180 TPS**. A conta de vazão é: `backlog ÷ 180` segundos para drenar. Um lote standard acima de aproximadamente 486 mil propostas (45 min × 180/s) não cabe no prazo nem com legado saudável. O SLO de J2 vale **dentro do envelope de carga** que a S6 vai medir. Fora dele, o evento continua ruim e é registrado como excesso de envelope, sem exclusão silenciosa. A contenção de 180 TPS é proteção, não indisponibilidade.

## Sinais de apoio (sem SLO próprio)

Oldest Message Age por fila, profundidade da DLQ, tempo de drenagem efetivo, estado do circuit breaker e saturação de in-flight. Eles servem para diagnóstico e alertas de causa, não entram no budget.

## Política de error budget

| Budget restante (30 d) | Ação |
|---|---|
| > 50% | Operação normal; experimentos de caos e mudanças liberados. |
| 25–50% | Mudanças de risco exigem plano de rollback; priorizar itens de confiabilidade abertos. |
| < 25% | Congelar experimentos e features não críticas na jornada afetada; foco em confiabilidade. |
| Esgotado | Somente correções de confiabilidade e segurança; postmortem obrigatório com ações. |
| Violação do invariante financeiro | Incidente imediato e postmortem, independentemente do budget. |

Queimas rápidas (alertas da S3) acionam resposta imediata sem esperar o budget acabar. Janelas sem dado não consomem nem devolvem budget.

## Revisão das metas

As metas foram definidas sem histórico contínuo suficiente. Uma revisão futura exige:

1. duas semanas de medição contínua com cobertura suficiente e distribuição observada;
2. o envelope de capacidade (S6), para avaliar a viabilidade dos prazos. A homologação atual mantém os alvos; não ajustá-los para esconder falhas.

Uma meta só sobe (ex.: J1 para 99,9%) quando o histórico mostrar folga consistente; nunca para esconder violações já observadas.

## Instrumentação após a S2

| Item | Medição | Situação |
|---|---|---|
| Buckets de 2 min e 45 min | Diagnóstico dos finalizados | Implementados; não substituem o denominador do avaliador |
| Pendentes vencidas | Avaliador sobre índice `sloOpen-createdAt` | Implementado para itens roteados com `sloOpen`; não roteados continuam fora |
| Histograma HTTP | Buckets de 100 ms e 200 ms | Implementados na ingestion-api |
| Conciliação 202 ↔ Inbox | Feita por experimento | Projeção contínua de divergências permanece pendente |

Labels permitidas: `service`, `env`, `lane`, `outcome`. Nenhum CPF, chave, ID de proposta ou trace como label (LGPD).
