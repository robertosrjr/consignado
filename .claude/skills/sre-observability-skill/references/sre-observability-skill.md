# SRE Observability: critérios de revisão

Consultar apenas as seções pertinentes. Estes critérios orientam decisões; não exigem acrescentar ferramentas, SLOs ou automação fora da tarefa solicitada. Requisitos específicos do produto devem vir do contrato existente, sem herdar números de outra PoC.

## 1. Contrato de medição e qualidade da evidência

Ao definir ou revisar um indicador de serviço, identificar:

| Aspecto | Pergunta que precisa ter resposta |
|---|---|
| Usuário e resultado | O que o usuário espera receber e em qual ponto da jornada? |
| População e identidade | A unidade é tentativa HTTP, mensagem, proposta canônica ou efeito no destino? |
| Bons/elegíveis | Quais eventos entram em cada parcela, inclusive duplicatas e falhas? |
| Exclusões | São definidas pelo contrato antes do teste ou escolhidas depois do resultado? |
| Tempo | Qual é a janela, o corte de observação, o início do prazo e a regra no limite? |
| Fonte e cobertura | Cliente, borda, servidor, estado persistido ou projeção? O que essa fonte deixa de observar? |
| Ausência | Como distinguir falta de demanda, perda de coleta, atraso e estado realmente pendente? |
| Meta e decisão | O alvo é hipótese, objetivo interno ou compromisso externo? O que muda se falhar? |

Um 4xx não determina sozinho a exclusão: cota excedida pelo cliente e proteção indevida dentro da cota têm impactos diferentes. Falhas de dependência podem consumir orçamento mesmo com a aplicação ativa. Latência de um erro rápido não deve demonstrar sucesso quando o indicador exige resposta correta.

Escolher poucos SLIs que representem a experiência. CPU, throughput e circuit breaker podem explicar uma falha sem serem SLOs isolados. Um SLA exige acordo e consequências; não chamar estimativa de fila de SLA. Separar orçamento de disponibilidade de critérios de integridade, como evitar efeito financeiro adicional.

Classificar as afirmações conforme a evidência: proposta, implementação, teste local, integração, carga e validação no ambiente. Configuração no Git, JSON válido e fonte declarada completa não comprovam sozinhos implantação, cobertura ou cumprimento de meta.

## 2. Jornadas assíncronas, coortes e capacidade

Usar esta seção quando há fila, processamento em lote ou decisão posterior ao aceite.

- Separar aceite original, roteamento, tentativa de processamento, decisão correta e recuperação. Rejeição comercial válida pode ser uma decisão correta; indisponibilidade e falha de serialização não devem virar rejeição comercial para melhorar o SLI.
- Construir a população a partir da entrada aceita, não apenas das conclusões observadas. Incluir propostas ainda não despachadas, invisíveis/em processamento e em DLQ, evitando contá-las duas vezes.
- Avaliar pendências ao vencer o prazo, mesmo se o consumidor parar. Histograma de finalizados mede os sobreviventes e pode esconder trabalho preso. DLQ não é conclusão correta e limite de tentativas não impõe, sozinho, idade máxima.
- Declarar coorte por aceite ou por vencimento e tratamento de itens ainda imaturos. Uma conclusão tardia resolve a pendência sem apagar a violação original. Evidência recebida tarde precisa de regra de correção auditável, separando horário do fato e horário da coleta.
- Preservar identidade, prazo e versão da política durante retries ou mudança de prioridade. Não converter replay idempotente automaticamente em efeito financeiro duplicado.
- Confrontar estados persistidos com observadores: gravar uma decisão e incrementar contador em memória são operações diferentes. Reinício, concorrência e falha entre essas etapas exigem reconciliação ou projeção recuperável.

Para capacidade, explicitar hipóteses: taxa de entradas realmente novas, duração do pico, mistura de classes, chamadas por operação e capacidade compartilhada no destino. Aumentar consumidores não amplia uma dependência com teto fixo. Piso de capacidade, empréstimo entre lanes e promoção por aging são mecanismos diferentes.

`Q / μ` estima esvaziamento sem novas chegadas. `Q / (μ − λ)` só é aproximação sob chegadas/saídas estáveis e `μ > λ`. Nenhuma fórmula é idade da mensagem, garantia individual ou promessa contratual. Medir também o trabalho ainda fora da fila e a amplificação de retries.

Em métricas financeiras, comparar a mesma coorte e base monetária. Recebido e confirmado na mesma janela podem pertencer a propostas diferentes; rejeições também retiram pendências. Valores/tickets e suas cores são KPIs de fluxo, não reconciliação exata. A apuração exata deve usar registros de negócio com precisão monetária adequada, fora das aproximações do TSDB.

## 3. Métricas e dashboards

Inspecionar o que está realmente exposto, incluindo nome, unidade, tipo, labels, buckets, intervalo e escopo do processo. Não escrever consultas sobre labels de elegibilidade, buckets ou séries imaginados.

| Verificação | Erro a evitar |
|---|---|
| Counters e resets | Somar contadores antes de tratar resets por série; confundir tentativas com operações únicas. |
| Gauges e snapshots | Aplicar `rate()`/`increase()` a totais de um relatório que representa um período fechado. |
| Histogramas | Prometer um limiar sem bucket correspondente; calcular média dos percentis de instâncias. |
| Janela da consulta | Usar intervalo de `rate()` incapaz de conter amostras suficientes para a coleta existente. |
| Denominador | Preencher zero/ausência com `clamp_min`, `or vector(0)` ou cor verde sem justificar o significado. |
| Agregação | Somar a mesma profundidade de fila reportada por várias réplicas, ou comparar limite por réplica com uso global. |
| Dimensões | Misturar ambientes, origens de relatório ou classes com contratos distintos. |
| Estado apresentado | Mostrar snapshot antigo como operação atual ou como disponibilidade mensal contínua. |

Ausência só pode ser convertida em zero quando o contrato e a cobertura sustentam esse significado, como um inventário conhecido e uma contagem comprovadamente nula. Sem denominador, apresentar sem eventos; sem evidência suficiente, apresentar medição incompleta. Não resolver falta de coleta apagando histórico.

Verificar frescor do produtor e da amostra interna: um push recente pode conter uma profundidade de fila antiga. Gateway acessível não comprova aplicação viva. Se usar Pushgateway, avaliar persistência de grupos, remoção/substituição de snapshots e inventário esperado. Coleta direta e push têm verificações de saúde diferentes; não impor uma topologia universal.

Usar dimensões limitadas e estáveis. Manter IDs de execução em artefatos controlados, sem criar uma série/grupo por teste. Valores agregados autorizados de negócio podem ser métricas; valores individuais, payloads, tokens e dados pessoais não devem ser expostos por conveniência. Mascarar logs não protege automaticamente spans ou labels.

Versionar JSON, UID e dependência de datasource/provisionamento conforme o projeto. Verificar denominadores, filtros, ausência e layout localmente; registrar consulta/renderização remota como pendente até testá-las no backend. Preparação local não inicia sincronização por conta própria.

## 4. Logs e tracing

Nos logs, conferir estrutura, eventos de transição/falha, correlação, volume, retenção e acesso. Preservar diagnóstico útil sem registrar payload, segredo ou dado pessoal. Não assumir centralização apenas pela existência de JSON em stdout; evitar log por evento em laço de alta vazão sem finalidade e orçamento.

No tracing, identificar instrumentação existente antes de adicionar filtros ou interceptores. Verificar contexto W3C nas fronteiras de rede, armazenamento intermediário e fila; restaurar contexto/MDC na thread real de execução, sem contaminar outra mensagem. Identidade de negócio e trace ID têm papéis distintos.

Propagação (`traceparent`/`tracestate`) não é exportação OTLP. Escolher relação pai/link conforme a operação e as convenções de mensageria aplicáveis; não impor uma única árvore a todo processamento assíncrono. Retentativas precisam ser distinguíveis. Não manter um span HTTP aberto apenas para representar longa espera em fila.

Verificar sampling herdado, filtros de atributos, baggage, conectividade, buffers e encerramento. Um teste em memória de propagação não comprova entrega ao coletor; enxergar serviços no backend não comprova a jornada completa. Traces amostrados ajudam a diagnosticar casos, sem substituir população exaustiva do SLI. Não alterar convenções automáticas de erro somente para tornar o painel saudável.

## 5. Alertas e orçamento de erro

Ao criar/revisar alertas, relacionar condição a impacto, responsável, ação e runbook. CPU/heap isolados normalmente são diagnóstico; integridade e falha completa com trabalho pendente podem exigir atenção mesmo com pouco tráfego.

Para indicador por eventos: orçamento = `elegíveis × (1 − alvo)`; burn rate = `(ruins / elegíveis) / (1 − alvo)`. Não converter automaticamente esse orçamento em minutos. Definir a ação ao consumir o orçamento e distinguir SLO interno de compromisso externo.

Escolher limiares e pares de janelas compatíveis com período, volume e tempo de resposta operacional. Não copiar parâmetros mensais para um teste curto. Piso de tráfego reduz ruído e também pode ocultar falha total com pouca demanda; considerar cobertura/frescor e sondas identificadas, sem misturar indiscriminadamente tráfego sintético e real.

Validar firing/resolved, ausência, reset, baixo tráfego, rota padrão, agrupamento, inibição e silêncios. Silêncio afeta notificação, não o orçamento medido. Entrega a tópico/fila/webhook não comprova reconhecimento humano: identificar o consumidor e o procedimento de resposta. A stack não detecta sua própria perda total sem observador independente.

Confrontar a duração exigida pelo alerta com o ciclo do mecanismo observado. Um circuito que alterna OPEN/HALF_OPEN pode interromper a condição antes de completar o `for`, mesmo durante indisponibilidade sustentada. Backlog também pode desaparecer por redrive, sem recuperação de negócio. Testar essas transições e registrar lacunas; não prolongar artificialmente o estado do circuito apenas para demonstrar firing. Escolher um detector de impacto sustentado com base em sinais realmente disponíveis e frescos.

## 6. Verificação proporcional à alteração

- **Documentação/contrato:** conferir fontes, semântica, exemplos calculados e referências. Não compilar aplicações só por editar texto.
- **Instrumentação/avaliador:** testes focados de resultado bom, ruim, sem eventos e cobertura incompleta; quando pertinente, incluir pendência, atraso, replay, erro técnico, concorrência e reinício.
- **Dashboard/regra:** validar arquivo e expressões com as ferramentas disponíveis; comparar população com massa conhecida. Distinguir validação estrutural de execução real da consulta.
- **Exportação/entrega:** testar o protocolo e o destino autorizado; não publicar fixtures em séries de operação real. Ausência de ambiente não invalida o trabalho local, mas limita a comprovação remota.

Na entrega, informar mudança, razão, evidências e limites relevantes. Atualizar o guia/inventário canônico do projeto se necessário; não criar relatórios, frameworks ou ferramentas extras sem utilidade para a solicitação.

### Casos úteis para conferir o raciocínio

| Situação | Comportamento esperado na revisão |
|---|---|
| Poucas operações terminam rápido e muitas ficam presas. | Questionar o histograma dos finalizados como SLO; incluir a população pendente e o vencimento. |
| Um snapshot sem eventos é apresentado como 100% disponível. | Separar ausência de demanda de sucesso e de perda de cobertura; não usar funções de counter no snapshot. |
| O cliente dá timeout, mas há aceite persistido e retry idempotente. | Manter a tentativa HTTP ruim, reconciliar uma operação canônica e avaliar sua decisão separadamente. |
| JSON local é válido, mas o Grafana está indisponível e o usuário pediu para não sincronizar. | Concluir preparação e verificações locais; não publicar nem declarar renderização remota validada. |
| O circuito abre, sonda e reabre antes do `for` do alerta. | Verificar continuidade da condição e detecção de impacto; não declarar cobertura apenas porque o circuito abriu. |
| O backlog zera porque as mensagens foram para DLQ. | Manter as operações na população pendente; conferir redrive e coleta válida da DLQ antes de declarar recuperação. |

## 7. Demonstrações de falha e recuperação

Usar quando a tarefa envolve ensaio de incidente ou engenharia do caos. Para detalhes de injeção, consultar a [skill de engenharia do caos](../../chaos-engineering/SKILL.md) quando disponível. Manter no projeto os parâmetros e roteiros específicos; esta skill não fixa duração, carga ou limiares universais.

- Definir estado estável, hipótese, coorte isolável e ponto de observação. Separar recuperação transitória, abertura de circuito e esgotamento deliberado até a DLQ: uma única falha prolongada não comprova indistintamente todos esses comportamentos.
- Inspecionar configuração efetiva de timeout, mínimo de chamadas do circuito, sondagem, visibilidade, backoff e redrive antes de escolher a massa e a duração. Limitar a falha ao orçamento de tentativas do cenário; abortar ou reclassificar explicitamente uma entrada inesperada na DLQ.
- Identificar estado volátil, ledger, chaves e sessões. Pausa e retomada do mesmo processo não comprovam recuperação após crash. Reiniciar para alterar uma configuração pode destruir a evidência ou tornar mensagens indecifráveis; não presumir que existe controle de falha em runtime.
- Para um controle de teste novo, justificar a menor mudança, restringir sua ativação e acesso e prever expiração/restauração independente do sucesso do controlador. Definir limites de carga, duração e interrupção proporcionais ao ambiente. Não inferir autorização para executar a partir de um pedido de plano.
- Em timeout, verificar possível efeito tardio antes de concluir que a operação falhou. Preservar identidade e idempotência no replay. Um ledger simulado só comprova integridade no escopo observado, sem garantir efeito financeiro único em produção.
- No redrive, preservar identidade, envelope e contexto de correlação; confirmar suporte da API do emulador/destino. Se a transferência for manual, confirmar envio antes de excluir a origem e tolerar repetição entre essas etapas. Não usar purga como recuperação.
- Medir separadamente início da falha, primeira evidência, pending/firing, publicação, ação e recuperação da coorte. Restaurar o serviço não basta se propostas ficaram na DLQ ou sem decisão. Uma execução fornece tempos observados, não uma estimativa estatística robusta de MTTD/MTTR.
- Registrar critérios atendidos, violados, evidência insuficiente e interrupção de segurança, preservando pendências no corte. Validar regra sintética e comportamento integrado como evidências distintas; documentar o último destino de notificação efetivamente comprovado.

## Referências primárias

- [Google SRE — Implementing SLOs](https://sre.google/workbook/implementing-slos/): escolher indicadores e separar especificação de implementação da medição.
- [Google SRE — Alerting on SLOs](https://sre.google/workbook/alerting-on-slos/): orçamento, janelas e limitações de baixo tráfego.
- [Prometheus — When to use the Pushgateway](https://prometheus.io/docs/practices/pushing/): adequação do push, ciclo de vida e séries persistentes.
- [OpenTelemetry — Traces](https://opentelemetry.io/docs/concepts/signals/traces/): contexto, spans e relações entre operações.

Consultar a documentação oficial da versão usada quando a tarefa depender de sintaxe, protocolo ou convenção específica. Estes critérios não homologam versões, metas ou infraestrutura do projeto.
