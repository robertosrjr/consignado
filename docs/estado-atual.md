# Estado atual da PoC

Atualizado em 23/10/2026. Este resumo descreve as execuções registradas, não o estado dos processos em tempo real.

## Onde paramos

**S0–S5 validadas; S6 com homologação de capacidade pendente.** O incremento atual é local, sem AWS. Os processos foram encerrados ao final dos ensaios.

| Etapa | Resultado |
|---|---|
| S0 — Pendências anteriores | Detector do circuito corrigido; L2 encerrado como INCOMPLETE |
| S1 — SLOs | Metas J1/J2 e política de orçamento de erro aprovadas |
| S2 — Medição contínua | Índice de prazos na Inbox, avaliador, regras e dashboard implementados, com limites de cobertura registrados |
| S3 — Alertas | Burn rate de J1/J2, DLQ e frescor do avaliador |
| S4 — Incidentes | Ponte local, fila ElasticMQ, consumidor com deduplicação e game day |
| S5 — Durabilidade | Estado e histórico do worker em DynamoDB; recuperação de órfãos; reinício real do worker validado |
| S6 — Capacidade | Quatro rodadas locais; nenhum limite sustentado homologado em todos os critérios |

Na última rodada da S6, a 1 requisição/s, o histograma do servidor indicou **p95 ≈ 89 ms e p99 ≈ 245 ms**. O limite de p99 é 200 ms. São quantis estimados; a janela curta não comprova o SLO móvel de 30 dias.

Próximo passo: investigar o custo do aceite, a escrita no DynamoDB e o agendamento do host; corrigir o ritmo do gerador e repetir a qualificação. Manter as metas atuais. Detalhes no [relatório de capacidade](sre/capacidade-local.md).

## Evidências e limites

- Último build completo registrado: `mvn verify -Pintegration`, 10 módulos, **473 testes sem falhas, erros ou testes ignorados**. É a validação de 23/10, não uma nova execução nesta revisão documental. [Evidência S5](../aidlc-docs/construction/sre-s5/validation.md).
- Reinício real do worker: seis propostas e seis averbações únicas, incluindo replays terminais e propostas aceitas enquanto o worker estava parado.
- O script local seleciona o repositório DynamoDB do worker. O padrão isolado da aplicação ainda é `memory`; selecionar `WORKER_PROPOSALS_TYPE=dynamodb` para usar a persistência.
- DynamoDB Local, ElasticMQ e ledger do mock permanecem em memória. O ensaio preservou banco, filas e legado durante o reinício do worker; não comprova recuperação após queda de todo o ambiente.
- Métricas de negócio e contadores de estado não substituem conciliação durável nem garantem contabilização exatamente uma vez.
- O J2 implementado não inclui propostas nunca roteadas nem itens antigos sem `sloOpen`. J1 ainda não detecta sozinho um 202 sem Inbox ou um 429 indevido. Ver [SLOs e limites](sre/slo.md).
- Tracing local foi observado no Jaeger. Runtime Lambda/AWS, datasource CloudWatch e adoção completa pelo Terraform continuam sem homologação atual.
- Business Fingerprint v2 permanece opt-in e desativado. Metas de 10.000 req/s na entrada e capacidade completa sob o teto nominal de 180 TPS não foram homologadas.

## Escopo

O foco é ingestão massiva, controle de vazão, idempotência, resiliência e observabilidade. **MFA, step-up e aprovação transacional ficam fora da PoC**, por decisão do usuário. Os documentos de MFA são estudos, sem compromisso de implementação. S7 e expansão para AWS ficam fora do incremento atual.

## Onde consultar

- [Plano e situação S0–S7](../aidlc-docs/inception/sre-evolucao/workflow-plan.md)
- [Estado do workflow AI-DLC](../aidlc-docs/aidlc-state.md)
- [Guia SRE](sre/README.md)
- [Fechamento histórico de 18/10](sre/fechamento-poc-sre-observabilidade.md)
- [Índice da documentação](README.md)
