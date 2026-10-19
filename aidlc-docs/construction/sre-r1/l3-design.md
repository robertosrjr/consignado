# R1/L3 — circuito, proteção e detecção

Data: 18/10/2026. Continuidade solicitada pelo usuário: “conseguimos Seguir para o L3: circuit breaker, verificando proteção, alertas e recuperação”. A execução começa com o ensaio local exigido pelo plano; não depende de iniciar a EC2.

## Hipótese

Com 20 propostas e falhas HTTP 503, o circuito abre após a amostra mínima, o loop deixa de retirar mensagens, as tentativas ficam preservadas durante OPEN e a restauração permite sondagem e conclusão das mesmas identidades. A notificação é uma hipótese separada: o detector atual exige uma condição contínua que o ciclo automático pode interromper.

## Configuração inspecionada

`WorkerProperties.Legacy` e `LegacyHttpAdapter.breakerConfig`: mínimo de 20 chamadas, janela de 100, limiar de falha/chamada lenta de 10%, lentidão acima de 2 s, timeout padrão 3 s, OPEN por 10 s com transição automática e cinco permissões em HALF_OPEN. `ProposalDispatchLoop` bloqueia novas retiradas em OPEN e permite somente um processamento em voo durante PROBING. Trabalho já em voo pode terminar após a abertura.

O controle do mock já tem expiração e restauração independente do gerador. Não alterar defaults para obter gráficos ou firing. O redrive local existente é de três recebimentos; não elevá-lo para sustentar artificialmente o cenário.

## Ensaio de integração local

`LegacyCircuitRecoveryTest` combina o cliente HTTP real, um servidor de teste local, Resilience4j, o adaptador de disponibilidade, o loop de despacho e a fila em memória com redrive de três recebimentos. O processamento é síncrono, uma chamada por vez, com um seam que traduz o resultado HTTP em COMPLETED/RETRY; não usa Inbox nem o caso de uso completo de persistência.

1. Enfileirar 20 identidades sintéticas, com valor de R$ 1.000 por chamada.
2. Retornar 503 nas primeiras 20 chamadas; esperar abertura do circuito.
3. Avançar apenas o relógio da fila para tornar os retries elegíveis. Executar 100 passos enquanto OPEN: nenhum despacho e nenhuma chamada adicional.
4. Inspecionar os recebimentos da fila de teste: todos na segunda entrega. Essa inspeção consome uma entrega e devolve a mensagem; é explicitamente um recurso do ensaio, não um procedimento para inspecionar SQS durante incidentes.
5. Restaurar HTTP 200 e aguardar HALF_OPEN automático, sem chamar o método de transição. O prazo de espera do teste é 30 s; a configuração do circuito permanece 10 s.
6. Observar HALF_OPEN nas primeiras quatro confirmações e CLOSED após a quinta; drenar as demais.
7. Exigir 20 acknowledgements, 20 identidades únicas observadas no servidor, zero replay de sucesso e zero DLQ. As 40 chamadas totais são 20 falhas e 20 sucessos, não 40 propostas.

O servidor é encerrado em `try-with-resources`. O teste não chama a AWS, não exporta resultados para dashboards e não envia alertas. Não comprova concorrência real, prefetch SQS, persistência, aceite HTTP da ingestão, ledger financeiro do mock nem conciliação dos SLIs. A igualdade de identidades e os retornos confirmados não substituem o relatório conciliado.

## Validação do detector existente

`config/prometheus/tests/consignado-l3.test.yml` exercita a regra versionada, sem modificá-la, com passo de cinco segundos e observações em 1, 3 e 4 minutos:

- OPEN contínuo com backlog e push fresco: firing em 3 min e resolução em 4 min após CLOSED.
- OPEN por 10 s alternado com HALF_OPEN por 5 s, mantendo backlog: nunca completa o `for: 2m`.
- Sem backlog, push antigo e push ausente: nenhum firing de indisponibilidade do legado.

A sequência intermitente é uma entrada sintética plausível, não uma cronologia capturada no runtime. Ela demonstra a lacuna lógica da regra. Um alerta ausente nessa situação não comprova dependência saudável. A fila deve continuar sendo examinada mesmo quando sai do estado visível ou vai para DLQ.

## Gate para demonstração com a stack

Antes de novos inputs, fechar ou classificar as lacunas dos artefatos L2 existentes; não reenviar sua proposta para compensar coleta incompleta. Em seguida, conferir saúde, sessões novas explicitamente identificadas, fila e DLQ vazias, controle neutro, relógios e telemetria fresca. Usar coorte nova isolável.

Coorte inicial de 20, no máximo 40 somente com revisão do orçamento de tentativas; erro 503 determinístico por no máximo 15 s. Restaurar antes se houver dois recebimentos de qualquer mensagem ou entrada inesperada em DLQ. Não consumir mensagens só para inspecionar o contador de recebimentos: usar a observação do consumidor ou uma fonte de evidência que não altere a entrega.

Registrar eventos do circuito com tempos, tentativas e chamadas, inclusive trabalho já em voo. Scrape de 15 s e push periódico podem perder estados de 10 s: gauge no Grafana sozinho não é prova completa das transições. Preservar logs/eventos e traces como evidência complementar.

Recuperação exige reconciliar todas as identidades e valores, conferir filas/DLQ e preservar pendências. Registrar a não ocorrência esperada do alerta curto; não prolongar a falha até esgotar o redrive para forçar notificação. Publicação SNS firing/resolved de um detector de impacto sustentado exige um desenho posterior compatível com o orçamento de tentativas.

## Evolução da detecção

O timer `worker.legacy.call` expõe resultados `confirmed`, `rejected`, `transient_failure` e `error`. Ele envolve a porta inteira: falha transitória pode incluir recusa local do circuito, não só tentativa HTTP no destino. Antes de desenhar outro detector, separar essas populações e verificar séries exportadas, denominadores, frescor e trabalho pendente. Não criar uma regra baseada em nomes supostos ou confundir ausência de chamadas durante OPEN com sucesso.

Manter proteção funcional, detecção de impacto, entrega SNS e atendimento como critérios separados. Enquanto não houver prova integrada, R1/L3 permanece parcial.
