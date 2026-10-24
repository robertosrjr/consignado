# O seu SRE mostra o quê?

**Recorte histórico:** este artigo descreve o primeiro ciclo, encerrado em 18/10/2026. Entregas posteriores, incluindo durabilidade S5 e homologação pendente S6, estão no [estado atual](../estado-atual.md). Não usar o artigo como inventário da implementação atual.

*O que aprendemos ao seguir uma proposta da entrada à recuperação — e ao testar se os alertas acompanhavam essa história.*

Cinco propostas entraram. Cinco foram confirmadas. Nenhuma ficou pendente. Os R$ 5 mil do teste bateram na conciliação.

O relatório de SRE marcou **FAIL**.

E estava certo.

Tínhamos feito dez chamadas: cinco propostas novas e cinco reenvios. Uma levou 314 milissegundos; as outras nove ficaram abaixo de 100. Nossas metas eram 95% das tentativas elegíveis com aceite correto em até 100 ms e 99% em até 200 ms. Naquela amostra, ficamos em 90% nos dois critérios.

A recuperação funcionou. A meta de latência falhou. Guardamos os dois resultados, sem atribuir a demora a uma causa que ainda não tínhamos investigado.

**[Abrir PRINT 1 — Grafana: SRE — Experimentos (L1)](http://52.67.178.137:3000/d/consignado-sre-experiments?from=1792330980000&to=1792331100000&timezone=browser)**

*Cole a captura aqui. Arquivo da imagem: `./imagens/artigo-o-seu-sre-mostra-o-que-v2/evidencia-relatorio-l1-fail.png`.*

*Para este print:* [abrir SRE — Experimentos](http://52.67.178.137:3000/d/consignado-sre-experiments?from=1792330980000&to=1792331100000&timezone=browser). Para reproduzir os números deste trecho, prefira o [relatório L1 preservado](../../applications/.local/legacy-l1-20261018-03/capture-02/report/report.md): cinco confirmadas, R$ 5 mil e FAIL de latência. O painel mostra o último relatório publicado; confira a execução antes de capturar.

Foi um dos momentos em que a nossa PoC de consignado começou a responder perguntas que um painel verde, sozinho, não respondia.

Até ali, a conversa tinha passado pelo caminho conhecido: subir o Grafana, fazer as métricas aparecerem, ajustar consultas, criar alertas. Mas começamos a cobrar mais das telas.

**O seu SRE mostra o quê?**

Na nossa aplicação, a API recebe a proposta e registra a entrada. Um dispatcher encaminha o trabalho para uma fila. O worker consome a mensagem e chama o legado. A decisão acontece depois do aceite HTTP.

Para aquela proposta, o HTTP 202 era só o começo.

Resolvemos observar isso com uma falha controlada. Pausamos o worker por quatro minutos e meio, mantendo a API disponível. Enviamos dez propostas e dez reenvios. Recebemos vinte respostas HTTP 202.

Enquanto isso, dez mensagens esperavam na fila.

**[Abrir PRINT 2 — Grafana: Backlog e Drenagem (pausa do worker)](http://52.67.178.137:3000/d/consignado-backlog-drain?from=1792273200000&to=1792273680000&timezone=browser)**

*Cole a captura aqui. Arquivo da imagem: `./imagens/artigo-o-seu-sre-mostra-o-que-v2/painel-backlog-pausa-worker.png`.*

*Para este print:* [abrir Backlog e Drenagem — pausa de 17/10, 18:40–18:48 (Brasília)](http://52.67.178.137:3000/d/consignado-backlog-drain?from=1792273200000&to=1792273680000&timezone=browser). Capture a fila standard e a retomada; a telemetria do worker fica antiga durante a pausa. O valor de dez mensagens foi conferido diretamente na fila, conforme a [evidência do exercício](exercicio-falha-worker.md), e não deve ser inferido de uma amostra ausente no gráfico.

A entrada estava funcionando. O processamento tinha parado. Quem acompanhasse apenas o sucesso HTTP teria uma visão incompleta do serviço entregue.

O alerta de ausência de métricas recentes do worker disparou. Acompanhamos sua publicação no SNS e, depois da retomada do processo, o envio da recuperação. Conferimos as mesmas dez identidades: todas confirmadas, filas vazias.

**[Abrir PRINT 3 — Grafana: SRE — Operação (alerta e recuperação)](http://52.67.178.137:3000/d/consignado-sre-overview?from=1792273200000&to=1792273920000&timezone=browser)**

*Cole a captura aqui. Arquivo da imagem: `./imagens/artigo-o-seu-sre-mostra-o-que-v2/evidencia-alerta-recuperacao-worker.png`.*

*Para este print:* [abrir Prometheus — histórico de ConsignadoPushMissing](http://52.67.178.137:9090/graph?g0.expr=ALERTS%7Balertname%3D%22ConsignadoPushMissing%22%7D&g0.tab=0&g0.range_input=15m&g0.end_input=2026-10-17%2021%3A52%3A00) e [SRE — Operação no intervalo da falha](http://52.67.178.137:3000/d/consignado-sre-overview?from=1792273200000&to=1792273920000&timezone=browser). [Alertmanager](http://52.67.178.137:9093/#/alerts) mostra alertas atuais; não guarda uma tela histórica de resolved. A publicação SNS está documentada na [linha do tempo do exercício](exercicio-falha-worker.md).

O alerta detectou a interrupção da telemetria. A conferência das propostas mostrou a recuperação do negócio. Cada evidência respondeu a uma pergunta diferente.

Esse trabalho aconteceu em laboratório: aplicação e emuladores locais, observabilidade na EC2 e legado simulado. Os valores são nominais, sem liberação real de crédito. Também fomos nós que programamos a retomada do worker; o teste preservou sua memória. Recuperação após crash continua sendo outra prova a fazer.

**As suas métricas estão mostrando o quê?**

Passamos a acompanhar propostas novas, confirmações, rejeições, valores recebidos e confirmados, além do ticket médio. Queríamos saber quanto trabalho tinha entrado e qual resultado ele tinha produzido.

Então surgiu uma pergunta que parecia pequena: por que o valor recebido mudava ao atualizar o painel, mesmo sem novas propostas?

A consulta estimava incrementos de contadores numa janela de tempo. Servia para observar o fluxo. As duas casas decimais do cartão, porém, davam uma aparência de precisão que aquela consulta não podia garantir.

Para fechar o resultado de um experimento, passamos a cruzar os registros do cliente, da entrada e do worker. A conciliação identifica as propostas daquela execução e separa confirmadas, rejeitadas e pendentes, com seus valores.

Isso mudou a leitura do negócio. Dez tentativas podem representar cinco propostas. Um reenvio aceito não acrescenta outro valor recebido. Uma rejeição comercial pode ser uma decisão correta do sistema. Uma confirmação tardia resolve a pendência, mas preserva a violação do prazo.

Também aprendemos a desconfiar de subtrações fáceis. Valor recebido menos valor confirmado num intervalo não é, automaticamente, dinheiro perdido ou valor preso na fila. Pode haver rejeições e propostas de períodos diferentes. Para afirmar o saldo pendente, precisamos acompanhar o mesmo conjunto de propostas.

CPU e memória continuam ajudando no diagnóstico. A conciliação permite explicar a consequência: quais operações terminaram e quais ainda exigem atenção.

**[Abrir PRINT 4 — Grafana: Negócio (teste de 19/10, 12:14–12:20)](http://52.67.178.137:3000/d/consignado-business-overview?from=1792422840000&to=1792423200000&timezone=browser)**

*Cole a captura aqui. Arquivo da imagem: `./imagens/artigo-o-seu-sre-mostra-o-que-v2/painel-negocio-propostas-valores.png`.*

*Para este print:* [abrir Negócio — últimos 30 minutos](http://52.67.178.137:3000/d/consignado-business-overview?from=now-30m&to=now&refresh=10s). Para os totais conciliados da coorte, use [SRE — Experimentos](http://52.67.178.137:3000/d/consignado-sre-experiments?from=now-30m&to=now). O painel Negócio estima o fluxo do período; uma captura da nova execução não representa os cinco itens históricos do L1.

*Nova execução para preparar as capturas, em 19/10/2026:* [abrir Negócio no intervalo de 12:14–12:20 (Brasília)](http://52.67.178.137:3000/d/consignado-business-overview?from=1792422840000&to=1792423200000&timezone=browser), [SRE — Operação](http://52.67.178.137:3000/d/consignado-sre-overview?from=1792422840000&to=1792423200000&timezone=browser) e [relatório local desta execução](../../applications/.local/article-20261019-capture/report/report.md). Foram enviadas cinco propostas de R$ 1.000 e cinco reenvios, com dez POSTs validados pelo JMeter. Este teste não injetou a falha 503 histórica. Telas da aplicação: [Swagger da entrada](http://127.0.0.1:8080/swagger-ui.html) e [Swagger do legado simulado](http://127.0.0.1:8081/swagger-ui.html).

SLI e SLO ganharam esse significado concreto. Medimos aceite correto, rapidez do aceite e decisão no prazo; aplicamos metas às populações e janelas declaradas. Ainda não temos um SLA externo ou uma medição contínua que sustente uma promessa mensal de disponibilidade.

**E os seus alertas estão alertando o quê?**

Criamos sete regras para falhas de coleta, métricas antigas, erros e latência na entrada, processamento parado, drenagem elevada e circuito aberto com trabalho pendente.

Na regra de erros da API, por exemplo, exigimos mais de 5% de respostas 5xx e pelo menos cem chamadas na janela de cinco minutos, mantendo a condição por cinco minutos. O piso reduz a sensibilidade a amostras pequenas. Também cria uma limitação: uma falha com pouco tráfego pode passar sem disparo.

Esse compromisso precisa estar claro para quem depende do alerta. Uma regra configurada não garante que todas as falhas relevantes serão detectadas.

Encontramos um exemplo disso ao testar o circuit breaker do worker.

No ensaio local, vinte chamadas com falha abriram o circuito. Cem novas passagens pelo loop não fizeram novos despachos. Depois da espera de dez segundos, o circuito entrou em sondagem. Cinco respostas bem-sucedidas permitiram fechá-lo, e as vinte identidades foram recuperadas, sem DLQ.

**[Abrir PRINT 5 — Log local: testes do circuit breaker L3](../../applications/.local/article-20261019-tests.log)**

*Cole a captura aqui. Arquivo da imagem: `./imagens/artigo-o-seu-sre-mostra-o-que-v2/evidencia-testes-l3-circuit-breaker.png`.*

*Para este print:* abrir no editor o [log dos testes executados em 19/10/2026](../../applications/.local/article-20261019-tests.log), buscando `LegacyCircuitRecoveryTest` e o resumo de 28 testes. A sequência e as verificações estão no [teste L3](../../applications/worker/src/test/java/br/com/consignado/platform/worker/e2e/LegacyCircuitRecoveryTest.java) e na [evidência detalhada](exercicio-circuit-breaker-l3.md). É uma saída de teste local; não há painel integrado que comprove esse ensaio.

Usamos HTTP real e fila em memória nesse ensaio. Foi uma prova da proteção nesse escopo, com processamento síncrono. A demonstração com a aplicação completa e SQS permanece pendente.

Quando olhamos o alerta, apareceu o desencontro: ele exigia dois minutos contínuos em OPEN, enquanto o circuito começava a sondar após dez segundos.

Testamos a regra com uma sequência alternando OPEN e HALF_OPEN, mantendo trabalho pendente e métricas recentes.

**O alerta não disparou.**

**[Abrir PRINT 6 — Arquivo local: cenários promtool OPEN/HALF_OPEN](../../infrastructure/Configuration/observability/config/prometheus/tests/consignado-l3.test.yml)**

*Cole a captura aqui. Arquivo da imagem: `./imagens/artigo-o-seu-sre-mostra-o-que-v2/evidencia-promtool-open-half-open.png`.*

*Para este print:* abrir os [cenários promtool L3](../../infrastructure/Configuration/observability/config/prometheus/tests/consignado-l3.test.yml) junto da saída `SUCCESS` do comando abaixo, executado novamente em 19/10/2026. A ausência esperada de alerta no caso alternado é parte do teste; a saída resumida sozinha não mostra essa condição.

```powershell
& applications/.local/tools/prometheus-3.5.0.windows-amd64/promtool.exe test rules infrastructure/Configuration/observability/config/prometheus/tests/consignado-l3.test.yml
```

A regra reconhecia a abertura contínua, mas aquela alternância interrompia o tempo necessário para disparar. A proteção e a detecção tinham comportamentos diferentes. Registramos a lacuna, que ainda precisa ser corrigida.

Essa descoberta valeu mais do que acrescentar outro alerta à lista. Agora sabemos qual situação precisa ser coberta e temos um teste que a reproduz.

Os traces ajudaram a investigar outra ambiguidade. No erro transitório do legado, vimos uma proposta receber 503 e, depois, 200 na mesma jornada. As cinco propostas daquele experimento terminaram confirmadas, com os R$ 5 mil conciliados. Foi a execução do FAIL de latência que abriu este relato.

**[Abrir PRINT 7 — Jaeger: trace L1 com 503 e retry 200](http://52.67.178.137:16686/trace/055f4ce43f63e70323abc640456e4a0d)**

*Cole a captura aqui. Arquivo da imagem: `./imagens/artigo-o-seu-sre-mostra-o-que-v2/trace-jaeger-503-200.png`.*

*Para este print:* [abrir o trace L1 no Jaeger](http://52.67.178.137:16686/trace/055f4ce43f63e70323abc640456e4a0d). Expanda os spans do worker e do legado para mostrar o 503 e o retry 200 na mesma jornada. Se a retenção já removeu esse trace, consulte a [evidência preservada do L1](exercicio-falha-legado-l1.md); não substitua por um trace saudável. Para investigar a execução atual, use a [busca do Jaeger](http://52.67.178.137:16686/search?service=ingestion-api&lookback=1h).

No cenário de timeout, o cliente desistiu após aproximadamente três segundos, enquanto o servidor continuou executando. Uma tentativa posterior retornou sucesso. O Jaeger mostrou a sequência; o fechamento da conciliação desse cenário ainda está pendente.

É aí que a identidade da operação importa. Se a resposta se perdeu, o trabalho pode ter acontecido. Repetir a chamada precisa preservar a idempotência. Nos ensaios, conferimos que reenvios mantiveram a identidade das propostas, em vez de inflar os totais de negócio.

Também implementamos Business Fingerprint como opção para deduplicação semântica, conforme as regras de negócio. Sua ativação é uma entrega separada; os resultados dos reenvios destes exercícios não comprovam, por si, essa opção em operação.

A proteção está nessa combinação: identidade nas repetições, filas para absorver espera, limites de consumo para respeitar a capacidade do legado, retries e circuit breaker. A observabilidade nos ajuda a verificar o comportamento dessas peças e a localizar o que ficou pendente.

Micrometer, Pushgateway, Prometheus e Grafana sustentaram as métricas. OpenTelemetry e Jaeger permitiram verificar jornadas locais atravessando API, dispatcher, worker e legado. Logs deram contexto à investigação. Alertmanager publicou no SNS, com uma assinatura para encaminhar à SQS.

Temos evidências de disparo e recuperação até o SNS nos exercícios descritos. A entrega à SQS foi registrada em uma validação anterior, mas não foi reinspecionada nessas rodadas. Consumidor, reconhecimento humano e escalonamento continuam pendentes. O caminho completo de tracing no runtime AWS também precisa de validação própria.

Há outras fronteiras abertas: durabilidade após reinício, acompanhamento contínuo de pendências vencidas, recuperação de DLQ, carga sustentada e monitoramento externo da própria stack. Os testes pequenos não comprovam capacidade de produção. A stack concentrada numa EC2 também pode cair junto com os mecanismos que deveriam nos avisar.

Nosso ganho foi conseguir responder com evidências: estas propostas foram recuperadas; estes valores foram conciliados; este prazo falhou; este alerta deixou escapar uma condição; esta parte ainda precisa ser testada.

Na próxima revisão do seu dashboard, escolha uma proposta que entrou durante uma falha. Siga o caminho dela. Confira se o alerta ajudaria alguém a agir e como você provaria que o processamento terminou.

Qual resposta ainda falta no seu ambiente?

---

<details>
<summary>Evidências e limites deste relato</summary>

Versão final editorial, baseada nos registros de 17 e 18/10/2026. Os resultados são de experimentos controlados, sem movimentação financeira real ou homologação de capacidade de produção.

As capturas marcadas no texto devem ser salvas em `docs/sre/imagens/artigo-o-seu-sre-mostra-o-que-v2/`, usando exatamente os nomes dos links. As evidências devem preservar os limites descritos no artigo; em especial, o L2 permanece incompleto e o L3 foi validado localmente.

Links de captura preparados em 19/10/2026 com o IP público `52.67.178.137`. Após um próximo stop/start da EC2, confira o IP e atualize os links. Grafana pode exigir o login já configurado. Links para `applications/.local/` são artefatos locais ignorados pelo Git: servem para preparar as imagens nesta estação. Dados históricos dependem da retenção; uma tela sem dados não invalida nem reproduz a evidência preservada. Os intervalos fixos acima se referem aos episódios de 17 e 18/10, enquanto os links relativos (`now`) mostram a execução atual.

Na nova rodada de 19/10, a conciliação encontrou cinco aceitas e cinco confirmadas, nenhuma aberta e R$ 5.000 recebidos/confirmados. O relatório local ficou **FAIL por latência**: nove das dez tentativas abaixo de 100 ms e 200 ms (90%, abaixo das metas). Esse relatório não foi publicado sobre o snapshot histórico do painel Experimentos. Passaram também os 28 testes focados do worker e os cenários L3 do promtool. Grafana, Prometheus e Jaeger responderam HTTP 200; os três serviços tinham pushes recentes, e o trace histórico L1 foi localizado pela API. EC2 e aplicações foram mantidas ligadas para as capturas.

- [Contrato de medição](contrato-medicao.md): metas de 95% até 100 ms e 99% até 200 ms; populações, janelas e prazos explícitos.
- [Pausa do worker](exercicio-falha-worker.md): dez propostas recuperadas, alerta real e publicação de disparo/recuperação até SNS; processo preservado.
- [Falha transitória L1](exercicio-falha-legado-l1.md): cinco propostas novas, cinco reenvios, uma chamada 503 recuperada, R$ 5 mil conciliados e FAIL de latência.
- [Timeout L2](exercicio-falha-legado-l2.md): sequência de execução tardia e retry observada; conciliação final pendente.
- [Circuit breaker L3](exercicio-circuit-breaker-l3.md): ensaio local com vinte identidades e lacuna OPEN/HALF_OPEN reproduzida em testes de regras; validação integrada pendente.
- [Relatório conciliado](relatorio-local.md) e [dashboards](../../applications/observability/grafana/dashboards/README.md): totais por execução e distinção entre fluxo estimado e registros de negócio.
- [Tracing local](../manuais/tracing-local.md): jornadas verificadas no Jaeger e limites da validação no runtime AWS.
- [Alertas](alertas/README.md) e [manual técnico](../../infrastructure/Configuration/observability/ALERTAS.md): condições, persistência, notificações e evidências por rodada.
- [Configuração de Business Fingerprint](../../applications/ingestion-api/src/main/java/br/com/consignado/platform/ingestion/infrastructure/config/BusinessFingerprintConfig.java): ativação explícita da funcionalidade; não inferir uso a partir dos testes de reenvio.

</details>
