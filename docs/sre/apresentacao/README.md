# Apresentação: como a PoC detecta uma falha e recupera o processamento

Roteiro baseado no [exercício validado em 17/10/2026](../exercicio-falha-worker.md).
Duração sugerida: **12 a 15 minutos**, com o ambiente preparado antes da apresentação.
Este documento é um roteiro; criá-lo não inicia serviços ou recursos AWS.

## Mensagem central

> A API aceita as propostas, a fila mantém o trabalho pendente, a observabilidade detecta
> a interrupção do consumidor e, quando ele volta, o processamento continua. Conseguimos
> acompanhar esse caminho nas métricas, no alerta e nos traces.

O cenário é a **pausa temporária do worker**. A retomada é programada pelo controle do
experimento. Não apresentar isso como self-healing, reinício automático ou recuperação
após perda da memória do processo.

## Sequência para o apresentador

| Momento aproximado | Mostrar | Explicação sugerida |
|---|---|---|
| Antes da falha | Grafana SRE — Operação e Prometheus Alerts | “Os serviços enviam métricas recentes e não há alertas ativos.” |
| T+0 | Terminal de controle da pausa | “Vamos interromper o consumidor por quatro minutos e meio. A API e a fila continuam ativas.” |
| T+15 a 45 s | JMeter e leitura da fila local | “Enviamos dez propostas e dez reenvios. As vinte chamadas foram aceitas, mas existem dez propostas únicas aguardando processamento.” |
| T+2 a 3 min | Idade do push e estado `pending` no Prometheus | “O último envio de métricas do worker envelheceu. A regra aguarda persistência para evitar disparos por atrasos breves.” |
| T+3 a 4 min | Alerta `firing` e Alertmanager | “A condição persistiu. O alerta real foi encaminhado para o receiver SNS.” |
| T+4 min 30 s | Terminal da pausa e leitura das filas | “Retiramos a falha. O worker volta a consumir as mensagens que já estavam aguardando.” |
| Depois da retomada | Estados das dez propostas e Jaeger | “As mesmas dez propostas foram confirmadas, sem reenviar a carga. O trace mostra a espera entre publicação e consumo.” |
| Até aproximadamente T+9 min | Alerta resolvido e contadores SNS | “A telemetria voltou. O episódio terminou e a recuperação foi notificada, respeitando o agrupamento do Alertmanager.” |
| Encerramento | Quadro dos resultados | “Comprovamos detecção, notificação e recuperação do processamento neste cenário.” |

Os tempos são aproximados. Há intervalos de scrape/avaliação de 15 s, `group_wait=30s`
e `group_interval=5m`. Não reduzir esses tempos durante a demonstração para forçar o resultado.

## Telas para deixar abertas

Usar o IP atual da EC2; ele pode mudar depois de uma parada e nova inicialização.

| Tela | Endereço/caminho | O que observar |
|---|---|---|
| Grafana SRE — Operação | Porta 3000, `/d/consignado-sre-overview/consignado-sre-operacao` | Idade do push, tráfego da API e sinais do worker. |
| Grafana Backlog e Drenagem | Porta 3000, `/d/consignado-backlog-drain/consignado-backlog-e-drenagem` | Processamento e drenagem após a retomada. |
| Prometheus Alerts | Porta 9090, `/alerts` | `ConsignadoPushMissing`: inativo → pending → firing → inativo. |
| Alertmanager | Porta 9093 | Alerta ativo, fingerprint e receiver `consignado-sns`. |
| Jaeger | Porta 16686 | Busca por `worker`, operação `process proposal`, período da execução. |
| Terminal local | PowerShell na raiz do repositório | JMeter, fila local e estados das propostas. |

No Grafana, selecionar a instância local e um período que inclua todo o exercício, como
os últimos 30 minutos. Ajustar o Jaeger para o mesmo período.

**Durante a pausa**, os gráficos que dependem de métricas recentes do worker podem ficar
“Sem dados”. O próprio amostrador de filas está pausado junto com ele. Para comprovar as
mensagens aguardando, usar a leitura direta do ElasticMQ descrita abaixo. Um valor antigo
de profundidade no Grafana não é evidência da situação atual da fila.

O painel **SRE — Experimentos** apresenta o último relatório publicado. Ele não incorpora
esta execução automaticamente; não usá-lo como resultado do teste ao vivo sem nova coleta
e publicação válida. O relatório de dez propostas do exercício histórico não foi publicado ali.

## Preparação do operador, antes de compartilhar a tela

Pré-requisitos: AWS CLI autenticado e autorizado, SSH disponível, Java 25 para os serviços,
Java 21 e JMeter nos caminhos esperados pelo [wrapper](../../../jMetter/consignado/executar.ps1).
Usar massa fictícia e execução isolada. Não mostrar senhas, tokens ou a chave do legado
gerada pelo iniciador nas telas da apresentação.

### 1. Ligar a EC2 e obter o IP atual

No PowerShell local:

```powershell
aws sts get-caller-identity
# Se a sessão estiver expirada: aws login

$instanceId = 'i-082b5f183d18a8d25'
aws ec2 start-instances --instance-ids $instanceId --region sa-east-1
aws ec2 wait instance-status-ok --instance-ids $instanceId --region sa-east-1
$ec2Ip = aws ec2 describe-instances --instance-ids $instanceId --region sa-east-1 `
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text
$ec2Ip
```

Conferir a conta antes de agir. A instância desta PoC pertence à conta `247755251743`.
Conectar com o cliente SSH configurado para a chave. Para o PuTTY e a chave PPK desta estação:

```powershell
& 'C:\Program Files\PuTTY\plink.exe' -ssh `
  -hostkey 'SHA256:ae6EG7rhkNurvV2vupcpeAPdEAFZ2QWSKN1Z1rqgWjg' `
  -i 'C:\Developer\Key\kp-dev-observability-stack.ppk' "ubuntu@$ec2Ip"
```

O fingerprint acima foi conferido na sessão de 17/10/2026. Se a máquina/chave do servidor
for substituída, verificar a nova identidade antes de atualizar esse valor.

### 2. Iniciar a stack da EC2

Dentro da sessão SSH, executar:

```sh
sudo systemctl start containerd.service docker.socket docker.service
cd ~/observability
docker compose -f compose.sem-elasticsearch.yaml config --quiet
docker compose -f compose.sem-elasticsearch.yaml up -d
docker compose -f compose.sem-elasticsearch.yaml ps
```

Conferir Grafana, Prometheus, Pushgateway, Alertmanager e Jaeger. A stack já possui os
dashboards e as regras publicados. Não limpar volumes, métricas ou histórico.

### 3. Iniciar a aplicação local

Na raiz do repositório, com os JARs atualizados:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File applications/scripts/local-up.ps1 `
  -SreCapture -PushgatewayAddress "${ec2Ip}:9091" `
  -OtlpTracesEndpoint "http://${ec2Ip}:4318/v1/traces"
```

Se os artefatos não estiverem preparados, empacotar antes da apresentação, na pasta
`applications`, com `mvn -pl worker,local-env,ingestion-api,mock-legacy -am package -DskipTests`.
Esse comando não executa testes. Não iniciar uma segunda sessão sobre as mesmas portas;
verificar `applications/.local/pids.json` se o iniciador informar execução existente.

Esperar os serviços ficarem saudáveis e a telemetria estabilizar. Antes de começar, conferir:

- Filas locais vazias e sem outro gerador de carga ativo.
- Push recente dos três serviços e ausência de alertas/silêncios que prejudiquem o teste.
- Qualquer notificação de inicialização já tratada; anotar os contadores SNS de referência.
- Horário inicial e instância usados na apresentação.

No Prometheus, consultar:

```promql
time() - push_time_seconds{job=~"ingestion-api|worker|mock-legacy"}
```

## Execução ao vivo

### 4. Terminal A: pausar somente o worker com retomada em 270 s

O bloco abaixo adapta o controle usado no exercício validado para execução em um terminal
dedicado no Windows. Executar na raiz do repositório, com acesso ao processo Java. Manter
esse terminal aberto até aparecer a confirmação de retomada. Se a verificação de processo
ou permissão falhar, interromper o exercício e investigar; não substituir o PID à mão.

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = (Get-Location).Path
$workerId = [int](Get-Content applications/.local/pids.json -Raw | ConvertFrom-Json).worker
$processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$workerId"
if ($processInfo.Name -ne 'java.exe' -or
    $processInfo.CommandLine -notlike "*$repoRoot\applications\worker\target\app.jar*") {
    throw 'O processo não corresponde ao worker deste workspace.'
}
if (-not ('PocDemoPause' -as [type])) {
    Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class PocDemoPause {
  [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr OpenProcess(uint rights, bool inherit, int pid);
  [DllImport("kernel32.dll")] public static extern bool CloseHandle(IntPtr handle);
  [DllImport("ntdll.dll")] public static extern int NtSuspendProcess(IntPtr handle);
  [DllImport("ntdll.dll")] public static extern int NtResumeProcess(IntPtr handle);
}
'@
}
$workerHandle = [PocDemoPause]::OpenProcess(0x0800, $false, $workerId)
if ($workerHandle -eq [IntPtr]::Zero) { throw 'Não foi possível abrir o processo.' }
$paused = $false
try {
    if ([PocDemoPause]::NtSuspendProcess($workerHandle) -ne 0) { throw 'Falha ao pausar.' }
    $paused = $true
    Write-Host "Worker pausado às $([DateTimeOffset]::Now). Retomada em 270 s."
    $resumeAt = [DateTimeOffset]::UtcNow.AddSeconds(270)
    while ([DateTimeOffset]::UtcNow -lt $resumeAt) { Start-Sleep -Seconds 1 }
} finally {
    if ($paused) {
        $resumeCode = [PocDemoPause]::NtResumeProcess($workerHandle)
        Write-Host "Retomada às $([DateTimeOffset]::Now). Resultado nativo: $resumeCode (0 = sucesso)."
    }
    [void][PocDemoPause]::CloseHandle($workerHandle)
}
```

O `finally` solicita retomada ao terminar ou interromper normalmente o bloco. Fechar à força
o terminal/processo de controle pode impedir essa limpeza. Não encerrar o processo do worker:
o repositório atual fica em memória e o cenário deixaria de ser o mesmo.

### 5. Terminal B: executar a massa e conferir a fila local

Logo depois da pausa:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File jMetter/consignado/executar.ps1 `
  -Cenario Negocio -Quantidade 10 -Valor 1000 -Tenants 1
```

Esperado: vinte POSTs com HTTP 202, dez propostas novas e dez duplicatas. Anotar o caminho
JTL impresso pelo wrapper. Não repetir o comando para recuperar as propostas: elas já foram
aceitas. Não usar `-SreCapture` neste comando durante a pausa, pois o preflight exige snapshot
recente do worker. O `-SreCapture` do iniciador continua produzindo snapshots após a retomada.

Definir a função abaixo no terminal B para consultar somente as filas do ElasticMQ local:

```powershell
function Get-PocQueue([string]$Name) {
    $body = @{
        QueueUrl = "http://127.0.0.1:9324/000000000000/$Name"
        AttributeNames = @('ApproximateNumberOfMessages','ApproximateNumberOfMessagesNotVisible')
    } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri http://127.0.0.1:9324 `
        -Headers @{'X-Amz-Target'='AmazonSQS.GetQueueAttributes'} `
        -ContentType application/x-amz-json-1.0 -Body $body -TimeoutSec 5).Attributes
}
Get-PocQueue 'sqs-standard-lane-queue'
```

Esperado no ambiente isolado: dez mensagens disponíveis e zero em voo. A leitura é amostrada;
aguardar a chegada do dispatcher se ainda estiver abaixo de dez. Não é a fila AWS de alertas.

### 6. Acompanhar a detecção e o envio SNS

Exibir `ConsignadoPushMissing` no Prometheus e acompanhar `pending` → `firing`.
No Alertmanager, conferir `service=worker`, receiver `consignado-sns`, início do episódio,
fingerprint e ausência de silêncio/inibição. Esse é um alerta real produzido pela regra;
não usar o script de alerta sintético para representar esta etapa.

Consultas para acompanhar o canal:

```promql
alertmanager_notifications_total{integration="sns"}
alertmanager_notification_latency_seconds_count{integration="sns"}
alertmanager_notifications_failed_total{integration="sns"}
```

Comparar com a referência anterior ao teste. Esperar um envio concluído no disparo e outro
na recuperação, sem aumento nas falhas. Outros alertas podem alterar os mesmos contadores;
não atribuir incrementos a este episódio sem conferir a situação dos demais alertas.
A evidência desses contadores é do Alertmanager até o SNS. Entrega SQS e atendimento humano
são verificações separadas e não fazem parte deste roteiro validado.

### 7. Conferir recuperação sem reenviar a carga

Após a retomada indicada no terminal A, aguardar o consumo e conferir:

```powershell
Get-PocQueue 'sqs-fast-lane-queue'
Get-PocQueue 'sqs-standard-lane-queue'
Get-PocQueue 'sqs-dlq'

$jtl = 'CAMINHO_DO_JTL_IMPRESSO_NESTA_EXECUCAO'
$attempts = Import-Csv -LiteralPath $jtl
$canonicalIds = @($attempts | Where-Object {
    $_.label -like 'POST*' -and $_.sre_response_kind -eq 'RECEIVED'
} | Select-Object -ExpandProperty sre_canonical -Unique)
$snapshot = Get-Content applications/.local/sre/worker-snapshot.json -Raw | ConvertFrom-Json
$proposalStates = @($snapshot.states | Where-Object canonicalId -in $canonicalIds)
$canonicalIds.Count
$proposalStates | Group-Object status | Select-Object Name,Count
```

Esperado: dez identidades, dez estados `CONFIRMED`, filas e DLQ vazias. Preservar o JTL e
copiar o snapshot final antes do desligamento. Se faltar uma identidade, mantê-la como
pendente de verificação; não concluir sucesso apenas porque a fila esvaziou.

No Jaeger, buscar `worker` / `process proposal` no período desta execução. Abrir uma jornada
com os quatro serviços. Mostrar API, `put inbox`, `send proposal`, `process proposal` e
`POST /v1/averbacoes`. A espera aparece entre publicação e consumo. A resposta HTTP 202
da API já havia encerrado; a chamada ao legado deve mostrar HTTP 200.

O alerta deve desaparecer após voltarem os pushes e a avaliação do Prometheus. Aguardar
também o envio SNS de recuperação: o `group_interval=5m` pode postergá-lo.

## Fechamento da apresentação

> Dez propostas foram aceitas enquanto o consumidor estava interrompido. Detectamos a
> ausência de telemetria, notificamos o incidente e acompanhamos o processamento das mesmas
> propostas após a retomada. As evidências estão nos estados, nas filas, no alerta e no trace.

Registrar resultado, horários, fingerprint, referência dos contadores SNS, JTL, snapshot final
e um trace da execução. Não reutilizar horários/contadores do exercício histórico como se
fossem da nova apresentação. Os artefatos antigos podem servir como alternativa gravada,
identificada como evidência de 17/10/2026, caso o ambiente ao vivo esteja indisponível.

Este caso valida ausência de push. As outras seis regras, falha do legado, reinício com
perda de memória, consumo AWS SQS e exactly-once exigem seus próprios exercícios.

## Encerrar o ambiente após a apresentação

Depois da recuperação SNS e de preservar os artefatos:

1. Criar no Alertmanager um silêncio curto de manutenção para `ConsignadoPushMissing` e
   a instância local específica, com comentário e expiração. Não silenciar todo o ambiente.
2. Na raiz do repositório, encerrar os serviços locais:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File applications/scripts/local-down.ps1
```

3. Na sessão SSH da EC2, parar os containers e o Docker, preservando volumes:

```sh
cd ~/observability
docker compose -f compose.sem-elasticsearch.yaml stop -t 30
docker ps
# Prosseguir com a parada do daemon somente se não houver outros containers em execução.
sudo systemctl stop docker.socket docker.service containerd.service
```

4. No PowerShell local, parar a EC2 e confirmar o estado:

```powershell
aws ec2 stop-instances --instance-ids $instanceId --region sa-east-1
aws ec2 wait instance-stopped --instance-ids $instanceId --region sa-east-1
aws ec2 describe-instances --instance-ids $instanceId --region sa-east-1 `
  --query 'Reservations[0].Instances[0].State.Name' --output text
```

Esperado: `stopped`. Se a sessão expirar, renovar com `aws login`. Não apresentar o ambiente
como desligado sem confirmação. Os volumes da stack permanecem; o estado em memória dos
serviços e emuladores locais termina com os processos.

## Referências do projeto

- [SRE da PoC](../README.md).
- [Evidências do exercício validado](../exercicio-falha-worker.md).
- [Alertas, limites e runbooks](../../../infrastructure/Configuration/observability/ALERTAS.md).
- [Tracing e leitura da jornada](../../manuais/tracing-local.md).
- [Relatório conciliado por execução](../relatorio-local.md).
