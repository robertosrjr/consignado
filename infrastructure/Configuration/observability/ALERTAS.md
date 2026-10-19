# Alertas SRE da PoC Consignado

Regras: `config/prometheus/rules/consignado-sre.yml`. Os limites iniciais sao hipoteses a
calibrar com carga e SLOs acordados, nao metas contratuais. Nenhuma regra nova possui
`action=trigger_agentic_loop`: nao aciona o orquestrador de self-healing existente.

| Alerta | Condicao | Persistencia | Nivel |
|---|---|---|---|
| ConsignadoScrapeDown | alvo da stack com up=0 | 2 min | critical |
| ConsignadoPushMissing | ultimo push >120 s ou um dos tres jobs esperados ausente | 1 min | critical |
| ConsignadoIngestionErrors | 5xx >5%, com >=100 POSTs na janela de 5 min | 5 min | critical |
| ConsignadoIngestionLatency | p95 >500 ms, com >=100 POSTs na janela de 5 min | 10 min | warning |
| ConsignadoWorkerStalled | profundidade >0 e processamento=0 | 5 min | critical |
| ConsignadoBacklogDelayed | estimativa de drenagem >300 s, com fila >0 | 10 min | warning |
| ConsignadoLegacyUnavailable | circuit breaker=2 (aberto), com fila >0 | 2 min | critical |

Os filtros HTTP sao `service="ingestion-api",method="POST",uri="/v1/proposals"`.
4xx, inclusive cotas 429, nao contam como falta de disponibilidade nesta primeira versao.
Metricas HTTP sao agrupadas por env/service/instance, com resets tratados antes da soma.
Erros, latencia e Worker exigem push recente (<120 s); snapshots antigos nao mantem
alertas de negocio artificialmente ativos. Quando a coleta do gateway cai, PushMissing e
suprimido pela propria expressao e ScrapeDown representa o problema de telemetria.

Os push_time_seconds nao carregam env: seu alerta preserva job/instance e deriva service
do job. Ausencia total de um job identifica apenas o servico, sem inventar uma instancia.
O teste de ausencia cobre tres jobs esperados neste gateway dedicado; nao detecta uma
replica que nunca enviou metricas quando outra replica do mesmo job continua ativa.
Estas regras assumem a topologia atual com Pushgateway. Para scrape direto, adaptar as
verificacoes de frescor antes de reutilizar. Ausencia de trafego, sozinha, nao gera alerta.

## Notificacao SNS (destino atual)

Topico Standard: `arn:aws:sns:sa-east-1:247755251743:alert-consignado`.
`config/alertmanager/alertmanager.yml` usa o receiver nativo consignado-sns para
notification=sre, com SigV4 na regiao sa-east-1 e send_resolved=true. A rota legada
do orquestrador permanece separada. Sem Lambda ou relay HTTP neste caminho.

Requisito: IAM Role/Instance Profile na EC2 `i-082b5f183d18a8d25`. A politica minima e
`config/alertmanager/sns-publish-policy.json`, que permite somente sns:Publish nesse
topico. `ec2-trust-policy.json` permite que EC2 assuma o role. Nao gravar credenciais
estaticas na configuracao. Se o SNS usar uma chave KMS gerenciada pelo cliente, revisar
tambem as permissoes KMS necessarias. IMDSv2 deve estar acessivel ao container; em rede
Docker bridge, verificar HttpPutResponseHopLimit=2, mantendo HttpTokens=required.

O corpo SNS e JSON versionado por `schemaVersion`, com status do grupo e uma lista de
alertas com status individual, labels, annotations, startsAt, endsAt e fingerprint.
Um grupo pode conter alertas disparados e resolvidos; consumidores devem ler status
individual. SNS/SQS podem entregar repeticoes: nao assumir exactly-once, usar fingerprint,
status e inicio do episodio para correlacionar eventos, preservando atualizacoes.

O template fica em config/alertmanager/templates/consignado-sns.tmpl. Validar com amtool
template render usando tests/sns-template-data.json; parsear a saida com um parser JSON.
A fixture cobre firing/resolved, acentos, aspas, barras e caracteres de controle.

Para chegar ao SQS, criar uma assinatura SNS do protocolo sqs e uma policy na fila
permitindo sqs:SendMessage pelo principal sns.amazonaws.com, restrita com aws:SourceArn
ao topico acima. Com RawMessageDelivery=false (padrao), o JSON do Alertmanager vem no
campo Message do envelope SNS; com true, ele ctega diretamente no corpo da mensagem.
Essa assinatura e a entrega na fila precisam ser verificadas separadamente.

As sete regras incluem `dashboard_path` nas annotations, com o caminho relativo do painel
para investigacao no Grafana. Os alertas de API e worker tambem incluem `jaeger_service` e
`jaeger_operation` para orientar a busca por traces no periodo do incidente. Esses campos
seguem no JSON SNS existente, sem alterar seu schema ou criar labels por requisicao.
`runbook` continua apontando para a secao correspondente deste documento. O consumidor
deve resolver os caminhos relativos usando os enderecos atuais do Grafana e da documentacao.

## Teste controlado de notificacao

O [script de teste](../../../applications/scripts/test-alert-notification.ps1) prepara um
alerta `ConsignadoNotificationTest`, com `env=validation`, `test=true` e `notification=sre`.
Ele nao possui `action=trigger_agentic_loop`. O identificador da rodada fica na annotation
`validation_id`, separado das labels. Usa a rota SNS existente; nao altera limiares, timers
ou silencia alertas reais. Expira automaticamente em 180 s por padrao.

A partir da raiz do repositorio, preparar sem enviar:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File applications/scripts/test-alert-notification.ps1 `
  -AlertmanagerUrl 'http://<IP_ATUAL>:9093' `
  -OutputDirectory applications/.local/alert-test-preparado-001 -PrepareOnly
```

Para enviar, executar novamente sem `-PrepareOnly`, com um diretorio novo. O script grava
`alert.json` antes do envio e `receipt.json` apos o aceite HTTP. O diretorio deve ser novo
para nao sobrescrever evidencias. **HTTP 200 comprova apenas entrada no Alertmanager**;
nao comprova publicacao SNS, entrega SQS ou reconhecimento humano.

Na configuracao atual, o primeiro envio aguarda `group_wait=30s`. A recuperacao pode esperar
o proximo `group_interval=5m`, mesmo quando o alerta ja desapareceu da lista de ativos.
Nao diminuir esses timers globais para acelerar o teste. Conferir a rota com amtool, os
silencios/inibicoes, a lista de alertas e os contadores `alertmanager_notifications_total`
e `alertmanager_notifications_failed_total`, filtrando `integration="sns"`. Incremento
de tentativas sem falha reportada, com conclusao da integracao, evidencia o envio pelo
Alertmanager; a verificacao no destino continua separada. Outros alertas simultaneos podem
incrementar os mesmos contadores, portanto preservar a situacao inicial e o fingerprint.

Este exercicio testa o canal de notificacao diretamente: nao prova que as sete condicoes
de Prometheus foram acionadas por uma falha real. A deteccao e exercitada pelos testes de
regras e, posteriormente, por cenarios controlados da aplicacao.

### Evidencia de 17/10/2026

- Sete regras ativas verificadas com `health=ok`; nenhuma condicao real estava disparada.
- `promtool check rules`, `promtool check config` e os 14 cenarios / 98 verificacoes de
  `promtool test rules` passaram, incluindo a versao com contexto de investigacao.
- `amtool check-config` passou; rotas warning/critical de `notification=sre` apontaram
  exclusivamente para `consignado-sns`, e um alerta sem rota apontou para `blackhole`.
- O template foi renderizado e parseado como JSON com estados firing/resolved e
  caracteres especiais; mantido schemaVersion 1.
- Teste sintetico iniciado as 18:27:09, America/Sao_Paulo, com expiracao as 18:30:09.
  Fingerprint `224ef3420b169a1d`; validation_id `0534c9fd-658f-4ee1-b543-a9580b19a9d3`.
- As 18:32:56, os contadores da integracao SNS passaram de seis para oito tentativas,
  com oito conclusoes e zero falhas. No intervalo, foi observado o alerta ativo e depois
  sua ausencia; nenhuma regra real estava disparada. Isso evidencia o ciclo de disparo
  e recuperacao pelo Alertmanager ate a publicacao SNS, sem confirmar recepcao no SQS.
- A fila SQS e o reconhecimento pelo consumidor **nao foram inspecionados nesta rodada**,
  por escolha do usuario. Nao ampliar o Instance Profile para consultar a fila apenas
  para esse teste; eventual verificacao posterior deve usar credenciais apropriadas.
- Aplicacoes e containers permaneceram em execucao. Nao foram alterados limiares,
  tempos de agrupamento, receiver ou rota do orquestrador.

Estado do teste e resultado ficam na EC2 em `~/observability/alert-cycle-20261017.json` e
`~/observability/alert-cycle-result-20261017.json`. A configuracao anterior das regras foi
guardada em `~/observability/alert-backups/20261017T212856Z/`. O helper PowerShell foi
validado com parsing e `-PrepareOnly`; esse dry-run nao enviou uma segunda rodada.

Estado da entrega: configuracao SNS ativada na EC2 apos associar o role
consignado-observability-role e verificar IMDSv2 pela rede do container. Primeiro alerta
sintetico publicado sem falhas de SNS. Conferir o resultado do teste de ciclo completo
em ~/observability/sns-test-result.json na EC2. A entrega SNS para a fila SQS foi validada posteriormente. Assinatura, policy e consumidor devem ser verificados separadamente quando houver alteracoes.

## Modelo alternativo de notificacao por e-mail (nao selecionado)

O Alertmanager em uso tem receiver padrao blackhole: avaliar uma regra nao significa
que um e-mail sera entregue. `config/alertmanager/alertmanager.email.example.yml` e um
modelo completo, NAO ativo. Antes de substituir a configuracao atual, informar:

- destinatario(s), remetente e usuario SMTP;
- servidor SMTP com porta STARTTLS (normalmente 587), conforme o provedor;
- credencial especifica de SMTP em arquivo, sem colar senha no chat ou no repositorio.

Guardar a senha no host em `config/alertmanager/secrets/smtp-password`, com acesso restrito
ao operador e ao usuario do container. A pasta config/alertmanager ja e montada read-only
em /etc/alertmanager: nao e preciso recriar o container para expor o arquivo nesse caminho.
O arquivo esta ignorado pelo Git. O Alertmanager nao substitui variaveis ${...} no YAML.
O modelo usa smtp_require_tls=true e auth_password_file; confirmar suporte do provedor.

O modelo agrupa por alertname/env/service/job, aguarda 30 s, agrupa novas notificacoes
por 5 min e repete critical em 1 h e warning em 4 h. Envia recuperacao. Inibe avisos
redundantes de latencia/backlog quando ha um alerta mais relevante para a mesma instancia.
Silenciar um ambiente nao impede a avaliacao das regras: so a notificacao.

Validar primeiro com `amtool check-config`; manter backup do YAML ativo e recarregar com
POST http://127.0.0.1:9093/-/reload. Confirmar a rota de notification=sre no amtool.
Depois testar um alerta sintetico isolado, autorizado, e sua recuperacao no e-mail real;
nenhum teste de e-mail foi enviado enquanto destino/SMTP nao estiverem configurados.

## Validacao das regras

Testes fora da pasta rules, para nao serem carregados pelo wildcard de producao:

```sh
promtool check rules config/prometheus/rules/consignado-sre.yml
promtool test rules config/prometheus/tests/consignado-sre.test.yml
```

14 cenarios / 98 verificacoes: disparos, transientes, pouco trafego, ausencia de push,
snapshots congelados, fila vazia, circuit breaker meio-aberto e persistencia. Nao param
servicos nem disparam notificacoes reais. Publicar apenas a regra, validar a configuracao
completa com promtool, fazer POST /-/reload e consultar /api/v1/rules e /api/v1/alerts.
Na EC2: `http://<IP_PUBLICO_ATUAL>:9090/alerts` e `http://<IP_PUBLICO_ATUAL>:9093` (consultar o IP da EC2 apos cada start).

## ConsignadoScrapeDown

Abrir /targets no Prometheus e conferir lastError e ultimo scrape. Verificar container,
DNS/rede Docker e logs. Ja ocorreu truncamento de GAUGE_HISTOGRAM no Pushgateway: manter
scrape_protocols=[PrometheusProto]. up=0 informa falha de coleta, nao prova queda do servico.
Se o proprio Prometheus ou a EC2 morrer, estas regras nao conseguem avisar: requer monitor
externo/heartbeat, fora desta primeira entrega.

## ConsignadoPushMissing

Verificar processo Java, configuracao do exportador e conectividade com porta 9091. Nao
confundir gateway acessivel com aplicacao viva. Grupos antigos persistem apos desligamento;
em parada local planejada, silenciar por service e instance com expiracao. Evitar silenciar
todos os alertas da stack. A espera total para push antigo e aproximadamente 3 min, mais
intervalo de avaliacao e agrupamento da notificacao.

## ConsignadoIngestionErrors

Conferir logs inbox_unavailable e tipo da excecao, recursos da maquina, DynamoDB e traces.
Na PoC houve ApiCallTimeoutException/ApiCallAttemptTimeoutException no DynamoDB Local.
503/timeouts podem ocorrer depois da gravacao: repetir a mesma Idempotency-Key. Comparar
contadores de entrada e resultados finais como etapas diferentes, sem exigir igualdade
imediata nem tratar rejeicao de negocio como falha tecnica.

## ConsignadoIngestionLatency

Conferir aquecimento/JIT, pool HTTP e latencia do banco; separar gerador de carga dos servicos.
Nao aumentar timeout global para esconder saturacao. O alerta mede p95 de POSTs agregados,
nao o limite por requisicao do JMeter. Fumaca com dez propostas nao atinge o volume minimo.

## ConsignadoWorkerStalled

Verificar fila/SQS, consumidores, permissoes, disponibilidade do legado e circuit breaker.
Conferir erros do amostrador: profundidade e taxa sao gauges aproximados; push recente nao
prova que a coleta interna da fila continua funcionando. Ainda falta uma metrica de ultimo
sucesso do amostrador e idade da mensagem mais antiga.

## ConsignadoBacklogDelayed

Comparar chegada/processamento e capacidade de drenagem. A metrica e profundidade/taxa
de processamento (infinita com backlog e taxa zero), nao idade da mensagem nem previsao
com novas chegadas. Em varias replicas, profundidade pode representar a mesma fila em
cada instancia: nao somar replicas para estimar backlog global sem revisar o modelo.

## ConsignadoLegacyUnavailable

Estado 2 significa aberto; 1 significa meio-aberto. Conferir legado, limites de capacidade,
latencia e respostas de falha. Manter protecao de capacidade e idempotencia ao recuperar.

## Evolucao

Definir SLOs, entao alertar por consumo de orcamento de erro em janelas curta/longa.
Instrumentar idade da mensagem mais antiga, DLQ e frescor do amostrador antes de prometer
alertas sobre esses sinais. CPU e heap sao diagnostico, nao pagina isolada nesta entrega.

Referencias: [Google SRE](https://sre.google/workbook/alerting-on-slos/) e
[Alertmanager](https://prometheus.io/docs/alerting/latest/configuration/).
