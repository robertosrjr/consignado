# SRE da PoC de consignado

Revisão: 23/10/2026. A observabilidade acompanha o aceite, a espera nas filas e a decisão do legado. O [estado atual](../estado-atual.md) concentra entregas, pendências e limites de validação.

## Por onde começar

| Documento | Finalidade |
|---|---|
| [SLOs v1](slo.md) | Metas J1/J2, população elegível, prazos e orçamento de erro |
| [Capacidade local](capacidade-local.md) | Quatro rodadas da S6 e motivo da homologação pendente |
| [Plano atual S0–S7](../../aidlc-docs/inception/sre-evolucao/workflow-plan.md) | Sequência de trabalho, entregas e próxima ação |
| [Alertas](alertas/README.md) | Rotas local e AWS, catálogo e investigação |
| [Runbooks e comandos](../../infrastructure/Configuration/observability/ALERTAS.md) | Condições exatas das regras e validação com promtool/amtool |
| [Dashboards](../../applications/observability/grafana/dashboards/README.md) | Painéis, consultas e interpretação dos indicadores |
| [Tracing local](../manuais/tracing-local.md) | Propagação W3C e jornadas observadas no Jaeger |
| [Postmortem S4](postmortem-gameday-s4-20261023.md) | Resultado do game day local |
| [Modelo de postmortem](postmortem-template.md) | Registro de impacto, causa e ações |

## Como medimos

**J1 — aceite:** disponibilidade de 99,5%, com 95% dos aceites bons em até 100 ms e 99% em até 200 ms. Observação no servidor da API.

**J2 — decisão:** 95% das propostas fast em até 2 min e 90% das standard em até 45 min. O avaliador consulta a decisão na Inbox e classifica os compromissos vencidos. Decisão tardia resolve a proposta, mas preserva a violação do prazo.

As metas usam janela móvel de 30 dias. Testes curtos fornecem evidência local, não disponibilidade mensal ou SLA externo. Ausência de eventos e falta de dados não significam 100% de sucesso. A [definição dos SLOs](slo.md) distingue o contrato desejado dos limites da medição implementada.

O avaliador offline reconcilia uma coorte finita de teste. Seus indicadores A/B/C e sua meta de aceite não devem ser misturados com J1/J2. Consulte o [contrato de medição](contrato-medicao.md), a [captura local](captura-local.md) e o [relatório local](relatorio-local.md).

## Ferramentas e interpretação

O Prometheus avalia métricas e regras; o Grafana apresenta os painéis; o Jaeger permite investigar a jornada. No incremento de 23/10, os ensaios usaram processos locais e scrape direto. A configuração histórica da EC2 usa Pushgateway e SNS → SQS. As duas configurações têm diferenças de cobertura de frescor e entrega de alertas.

- Requisição HTTP, proposta única e tentativa de processamento são populações diferentes.
- Recebido e confirmado em um período podem pertencer a coortes diferentes. Conciliar os registros antes de concluir que houve perda.
- Histogramas de finalizados não incluem propostas presas. J2 usa compromissos vencidos, respeitando as lacunas registradas na validação.
- `increase()` e quantis de histogramas são estimativas. Valores financeiros exigem conciliação por proposta.
- A divisão 70/30 é um piso sob contenção. Uma lane vazia empresta capacidade à outra.
- Drenagem estimada não é idade de mensagem nem garantia de prazo.
- Logs e traces preservam correlação sem payloads, CPF, tokens ou segredos. IDs de proposta não são labels de métricas.

## Histórico preservado

O [fechamento de 18/10](fechamento-poc-sre-observabilidade.md) encerra o primeiro ciclo. O incremento S0–S6 de 23/10 é posterior. Os documentos abaixo registram os resultados nas condições de cada execução:

- [Falha do worker](exercicio-falha-worker.md): pausa do processo, recuperação e traces; não foi teste de reinício.
- [Preparação de falhas do legado](preparacao-falhas-legado.md), [L1](exercicio-falha-legado-l1.md), [L2](exercicio-falha-legado-l2.md) e [L3](exercicio-circuit-breaker-l3.md).
- [Plano inicial SRE](plano-implementacao.md) e [plano AI-DLC R0–R4](../../aidlc-docs/inception/sre-resilience/plans/execution-plan.md).
- [Roteiro de apresentação do primeiro ciclo](apresentacao/README.md) e [artigo baseado nesse ciclo](artigo-o-seu-sre-mostra-o-que-v2.md).

Ao mudar instrumentação, regras ou painéis, atualizar o guia responsável e registrar a evidência. Manter o progresso no estado atual, sem copiar o histórico inteiro para cada README.
