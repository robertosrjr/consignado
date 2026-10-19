# Preparação local de falhas do legado — R0

Este incremento implementa o mecanismo de falha do [plano AI-DLC](../../aidlc-docs/inception/sre-resilience/plans/execution-plan.md). Não comprova ainda os cenários L1/L2/L3 na jornada completa, a entrega de novos alertas ou sua visualização no Jaeger. O [desenho da unidade](../../aidlc-docs/construction/sre-r0/unit-design.md) registra contratos e limites.

Validação local em 17/10/2026: 139 testes sem falhas no mock e suas dependências, com gates de formatação e cobertura aprovados. Ver [evidências e limites](../../aidlc-docs/construction/sre-r0/validation.md). A EC2 não foi acessada ou iniciada.

## Comportamento

O mock aplica uma política temporária de erro/latência dentro do mesmo processo. A expiração usa relógio monotônico e é verificada a cada leitura: novas chamadas voltam à baseline quando o prazo termina, mesmo se o script cliente encerrar. Não há dependência de uma chamada futura de restauração ou de tarefa agendada. Uma chamada que já capturou um atraso pode terminar depois da restauração; isso permite investigar o commit tardio após timeout.

Ledger, chaves e processos são preservados. A capacidade continua sendo verificada antes da simulação. Não há reset de métricas, purga de filas ou nova identidade de negócio. Sem o controle habilitado, a configuração estática de caos continua funcionando como antes.

## Ativação explícita numa execução futura

Executar da raiz do repositório, após o build. Os exemplos abaixo não foram executados contra os serviços nesta entrega.

```powershell
# Token só na sessão; não salvar no repositório nem enviar no chat.
$env:LEGACY_FAULT_CONTROL_TOKEN = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
& .\applications\scripts\local-up.ps1 -SreCapture -SreFaultControl -PushgatewayAddress '' -OtlpTracesEndpoint ''
& .\applications\scripts\sre-legacy-fault.ps1 -Action Status
```

Essa inicialização desliga explicitamente a exportação remota. Um exercício com Prometheus/Jaeger exige configurar e validar os destinos disponíveis antes da falha; não usar essa preparação isolada como prova de observabilidade ponta a ponta.

O iniciador habilita o perfil `sre-experiment` e `legacy.fault-control.enabled=true` somente nos argumentos do mock. Sem `-SreFaultControl`, força a flag a `false`. O token é recebido do ambiente e não é persistido ou impresso pelo controle. Não publicar nem encaminhar a rota por proxy.

## API de controle

Base: `http://127.0.0.1:8081/internal/sre/fault`. Todos os métodos exigem `X-SRE-Control-Token`, separado de `X-Api-Key`. O token precisa ter pelo menos 32 caracteres. O controle só existe com perfil e flag explícitos; sua inicialização falha se o token for inválido ou a baseline estática tiver erro/latência ativos.

| Método | Resultado |
|---|---|
| GET | Estado efetivo: `active`, `latencyMs`, `errorRate`, `remainingMs`. |
| POST | Ativa uma falha com prazo limitado; retorna 409 se outra ainda estiver ativa. |
| DELETE | Restaura a baseline; repetir é seguro. |

POST exige os três campos: latência entre 0 e 10.000 ms; taxa de erro finita entre 0 e 1; duração entre 1 e 300.000 ms; ao menos latência ou erro não nulo. Erro 1 produz falha determinística. Prazo de controle e atraso de uma chamada são grandezas diferentes. Respostas de estado bem-sucedidas têm `Cache-Control: no-store`.

Requisição com origem não loopback recebe 403. Headers de encaminhamento não são usados pelo controlador para liberar acesso; a implantação de teste deve preservar a origem da conexão, sem configuração de proxy que a reescreva. Token ausente/incorreto recebe 401. Perfil ou flag ausente deixa a rota inexistente. Este controle não é destinado a produção.

## Cliente e manifest

```powershell
# Só prepara um manifest; não acessa a rede nem exige token.
& .\applications\scripts\sre-legacy-fault.ps1 -Action Activate -DurationMs 2000 -PrepareOnly

# Executar somente dentro de um cenário com coorte e rollback preparados.
& .\applications\scripts\sre-legacy-fault.ps1 -Action Activate -ErrorRate 1 -DurationMs 2000
& .\applications\scripts\sre-legacy-fault.ps1 -Action Restore
& .\applications\scripts\sre-legacy-fault.ps1 -Action Status
```

O script só usa loopback, com porta configurável, consulta o estado antes da alteração e grava manifest em `applications/.local/sre/fault-control/`. O arquivo contém ação, parâmetros, horários UTC e estado observado, sem token ou corpo de negócio. `PREPARED` significa sem execução; `OBSERVED` confirma apenas a resposta do controle; `INCOMPLETE` indica que a ação não foi concluída ou sua resposta não foi comprovada. Não são resultados de SLI ou confirmação financeira.

Se o POST perder a resposta, uma falha pode ter sido ativada. Consultar Status/Restore; o prazo do mock continua valendo independentemente do cliente. Um erro de acesso ou de escrita do manifest não prova que a alteração no servidor falhou. Os arquivos são evidências técnicas locais, não devem ser publicados automaticamente.

## Gate antes do R1

- Conferir parâmetros efetivos e overrides de timeout, circuito, visibilidade, retry e redrive; a baseline de código está no [inventário AI-DLC](../../aidlc-docs/inception/sre-resilience/reverse-engineering/api-and-dependencies.md).
- Identificar a coorte e sua situação inicial na Inbox, worker, filas e ledger; não misturar propostas anteriores.
- Confirmar métricas e amostras de fila frescas, exportação de trace e rota de alerta, quando fizerem parte da hipótese.
- Ensaiar Status/Restore e confirmar baseline neutra antes de enviar propostas. Definir observação de tentativas, teto de duração e interrupção por DLQ inesperada.
- Preparar coleta JTL/worker e conciliação pelo [guia de captura](captura-local.md); o manifest do controle não substitui essa coleta.

O primeiro cenário futuro é L1, cinco propostas sob 503 transitório. A abertura do circuito não é critério para essa amostra. O disparo do alerta de legado permanece uma hipótese a investigar separadamente, por causa do `for` de dois minutos e das sondagens após dez segundos.

Para rollback durante um ensaio: parar novas entradas, executar Restore, confirmar Status e acompanhar a coorte até o corte. Não reiniciar processos nem apagar estado para simular recuperação. Ao terminar a sessão futura, encerrar os serviços pelo procedimento existente e remover a variável do token da sessão.
