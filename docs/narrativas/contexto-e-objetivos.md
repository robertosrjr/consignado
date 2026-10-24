# Contexto e objetivos da PoC

Revisão: 23/10/2026. Este documento explica o problema e as metas. Os resultados comprovados estão no [estado atual](../estado-atual.md).

## O problema

A plataforma recebe propostas de crédito consignado de parceiros e as encaminha a um legado de capacidade limitada. O cenário de teste simula um pico de entrada, como uma virada de folha, com requisições repetidas e momentos de indisponibilidade do destino.

Encaminhar todo o pico diretamente ao legado aumenta a disputa por conexões e o risco de timeouts. As retentativas acrescentam carga e podem repetir efeitos se o destino não reconhecer a mesma operação.

## A proposta

Separar aceite e processamento: a API registra a proposta na Inbox antes de responder `202`; o dispatcher escolhe uma fila; o worker controla o ritmo de envio ao legado. O `202` confirma o aceite, não a averbação.

| Capacidade | Objetivo |
|---|---|
| Ingestão | Meta de 10.000 requisições/s, com p95 abaixo de 100 ms; ainda sem homologação |
| Idempotência | Reconhecer reenvios da mesma chave e evitar novo efeito no destino, respeitando os limites de persistência e do contrato do legado |
| Controle de vazão | Limitar envios sob o teto nominal de 180 TPS do legado simulado |
| Prioridade | Pisos de 70% fast e 30% standard sob contenção, com empréstimo quando uma fila está ociosa |
| Recuperação | Retomar processamento após falhas sem perder a identidade da proposta |
| Observabilidade | Distinguir aceite, espera, decisão e falta de evidência |

A pontuação atual considera portabilidade, parceiro VIP e valor. Seguro acoplado, Spread, CET e saldo devedor de portabilidade não fazem parte do contrato implementado.

## Como avaliar

A PoC precisa conciliar propostas aceitas e decisões da mesma coorte, distinguir replays de novos efeitos e mostrar o comportamento durante falhas. Contadores e painéis ajudam na investigação, mas não substituem a conciliação dos registros.

Um replay reconhecido pelo mock legado é esperado em certas retentativas e não comprova averbação duplicada. O critério de integridade é ausência de segundo efeito para a mesma operação.

A S6 ainda não homologou capacidade sustentada: o último ensaio local reprovou o limite de p99 de 200 ms. O teto configurado de 180 TPS não é uma medição da vazão real de todo o sistema.

## Escopo

O foco é a plataforma de ingestão e processamento. Contratação, gestão de identidade do cliente, MFA e aprovação transacional não entram nesta PoC. O Core Banking é um mock; os resultados locais não comprovam conformidade regulatória ou comportamento de um banco real.

## Leituras

- [Visão técnica](../manuais/visao-tecnica.md)
- [Jornada da proposta](../jornadas/jornada-da-proposta.md)
- [SLOs e limites da medição](../sre/slo.md)
- [Resultados de capacidade](../sre/capacidade-local.md)
