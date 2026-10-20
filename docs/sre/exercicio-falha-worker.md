# Exercício SRE: pausa do worker, detecção e recuperação

Executado em 17/10/2026. Horários deste documento: America/Sao_Paulo (UTC−03:00).
Escopo: aplicação Java e emuladores locais; Prometheus, Alertmanager, SNS e Jaeger da EC2.
Não foi um teste de carga, reinício de processo ou homologação de SLA.

## Objetivo e método

Demonstrar uma falha real observável: com o worker temporariamente pausado, a API continua
aceitando propostas, as mensagens aguardam na fila e o envio de métricas do consumidor é interrompido.
O alerta deve disparar sem alteração de seus limites. Ao retirar a falha, as mesmas propostas
devem ser processadas sem novos envios pelo cliente.

O processo Java do worker foi identificado pelo PID e caminho do JAR do workspace. Uma
rotina de controle independente suspendeu somente esse processo por 270 segundos, manteve
um handle para o processo original e garantiu retomada em `finally`. API, legado e emuladores
permaneceram ativos. Nenhuma fila foi purgada, nenhum contador foi zerado e nenhum serviço
da stack de observabilidade foi parado durante a medição.

A retomada foi agendada pelo experimento: **não comprova self-healing ou reinício automático
do worker**. O que se verifica é a detecção da falta de telemetria e a retomada do processamento
pela aplicação depois de retirada a falha. Pausar preserva memória; não prova recuperação
após perda do processo ou durabilidade do repositório do worker.

## Massa e resultados

JMeter, cenário `Negocio`, dez propostas de R$ 1.000,00 e dez reenvios:

- Vinte POSTs com HTTP 202; dez respostas novas e dez duplicatas.
- Durante a pausa: dez mensagens disponíveis na fila local `standard`, nenhuma em voo.
- Após a retomada: as dez identidades canônicas da massa foram encontradas no snapshot do
  worker com estado `CONFIRMED`; nenhuma proposta dessa execução permaneceu aberta.
- Filas fast/standard e DLQ locais ficaram com zero mensagens disponíveis e zero em voo.
- Valor nominal da massa: R$ 10.000,00. Não foi reexportado como novo relatório de negócio;
  o painel SRE — Experimentos conserva a execução anteriormente publicada.

Essas leituras de fila usam **ElasticMQ local**, não a fila AWS de notificações. O SQS de
alertas não foi consultado, conforme a escolha do usuário para esta rodada.

## Linha do tempo observada

| Horário | Evidência |
|---|---|
| 18:40:55 | Worker pausado, com retomada agendada para 270 s depois. |
| 18:41 | JMeter concluiu as vinte chamadas; dez mensagens aguardavam na fila standard. |
| 18:43:15 | `ConsignadoPushMissing` observado em `pending`; último push do worker com aproximadamente 146 s. |
| 18:44:05 | Alerta observado em `firing` no Prometheus e ativo no Alertmanager, receiver `consignado-sns`. |
| 18:45:17 | Um envio SNS adicional concluído, sem falhas reportadas: contador passou de oito para nove. |
| 18:45:25 | Controle do experimento retomou o worker original. |
| 18:45:41 | Snapshot confirmou as dez propostas; filas e DLQ vazias. |
| 18:46:44 | Push do worker recente novamente; alerta ausente no Prometheus e Alertmanager. |
| 18:50:13 | Dois envios SNS do episódio concluídos: contador passou de oito para dez, com dez conclusões e zero falhas reportadas. |
| 18:50:34 | API, worker, mock legado e emuladores locais encerrados após preservar as evidências. |
| 18:50:48 | Containers parados; Docker, socket Docker e containerd confirmados inativos, sem remoção de volumes. |

Os instantes de `pending` e `firing` são momentos de observação, não o instante exato de
transição. A regra exige push mais antigo que 120 s por um minuto, acrescido dos intervalos
de scrape/avaliação e `group_wait=30s`. A recuperação SNS pode aguardar o próximo
`group_interval=5m`; ausência do alerta ativo não significa envio imediato do resolved.

Fingerprint do episódio real: `a46e2f2595286a4b`. Não foi usado o alerta sintético de teste.
Os limiares e as rotas permaneceram iguais aos da [configuração de alertas](../../infrastructure/Configuration/observability/ALERTAS.md).

## Jornada distribuída

A consulta ao Jaeger na janela da massa encontrou vinte traces HTTP. Dez jornadas de
propostas novas continham os quatro serviços `ingestion-api`, `dispatcher-local`, `worker`
e `mock-legacy`. Cada jornada tinha onze spans; todas as referências inspecionadas foram
resolvidas no mesmo trace e a chamada do worker ao legado retornou HTTP 200.

O intervalo entre o fim da publicação e o início do processamento ficou entre 228,94 e
230,82 segundos. Ele aparece como intervalo na linha do tempo; não existe um span específico
medindo toda a permanência na fila. As requisições HTTP de aceite encerram antes do consumo.

Exemplo de trace: `dfa251b058526093a9cff1b6506a2341`. Para abrir após ligar a stack, usar
`http://<IP_ATUAL>:16686/trace/dfa251b058526093a9cff1b6506a2341`, enquanto a retenção permitir.
O JSON agregado das dez jornadas foi preservado antes do encerramento.

## Evidências e limites

O diretório local da execução é `applications/.local/failure-drill-20261017-184041/`, ignorado
pelo Git. Inclui estado da pausa, JTL referenciado, identidades canônicas da massa, filas
durante a falha, resultado da recuperação, snapshot final e resumo do Jaeger. Os registros
do Prometheus/Alertmanager foram gravados na EC2 em `~/observability/failure-drill-records/`
e copiados para esse diretório local. Não anexar dados brutos ao artigo sem revisão.

Este episódio valida `ConsignadoPushMissing`. Não prova disparo real das outras seis regras,
rejeição comercial, falha do legado, redrive de DLQ, dispatcher Lambda na AWS, consumo SQS
de notificações ou efeito financeiro exatamente uma vez. Os testes offline das sete regras
continuam sendo uma evidência separada.

O episódio foi acompanhado desde o estado sem alerta até o disparo e a resolução. Os contadores
da integração SNS registraram dois envios adicionais concluídos, sem falhas reportadas, sem
alertas concorrentes das regras reais nas observações. Essa é evidência do canal até o SNS
pelo Alertmanager; não foi comprovada a recepção das mensagens no SQS nesta rodada.

O encerramento dos serviços locais e Docker ocorreu depois da preservação das evidências
e da verificação do envio de recuperação. Foi criado um silêncio de manutenção de quinze
minutos, restrito a `ConsignadoPushMissing` na instância local, depois de resolvido o incidente;
ele evita notificações da parada planejada, sem alterar os resultados do exercício.

**Encerramento concluído:** após o usuário renovar a sessão AWS, a identidade da conta e a
instância foram conferidas, e `StopInstances` foi executado para `i-082b5f183d18a8d25`, região
`sa-east-1`. O waiter `instance-stopped` terminou com sucesso e uma consulta posterior confirmou
`State=stopped`. A instância não foi terminada e os volumes foram preservados. A sessão expirada
e a falta de permissão do Instance Profile haviam impedido a parada inicial; nenhuma permissão
IAM foi ampliada para contornar essa restrição.

Os comprovantes locais de encerramento e envio SNS ficam junto dos demais artefatos, incluindo
`local-shutdown.json`, `failure-drill-notification-final.json` e `failure-drill-docker-shutdown.json`.
Volumes da observabilidade foram preservados. O estado em memória dos emuladores e do worker
terminou junto com os processos; os snapshots finais foram copiados antes disso.
