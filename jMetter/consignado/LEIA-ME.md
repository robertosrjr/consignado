# JMeter - Consignado

Plano para `POST /v1/proposals`. O gerador chama a API local; a aplicacao envia as metricas
ao Pushgateway. Use `executar.ps1`: seleciona um cenario, usa Java 21 apenas neste processo,
grava JTL/log com nomes unicos e retorna erro se houver falhas ou nenhuma amostra/POST.
O JMeter sozinho pode sair com codigo zero mesmo com assercoes falhando.

## Como executar

```powershell
Set-Location C:\Developer\Workspace\java\consignado\jMetter\consignado
# Sem chamadas HTTP: carrega o plano e executa a inicializacao.
powershell -NoProfile -ExecutionPolicy Bypass -File .\executar.ps1 -Cenario Validar
# 10 propostas variadas.
powershell -NoProfile -ExecutionPolicy Bypass -File .\executar.ps1 -Cenario Fumaca
# 10 novas de R$ 1.000 e um replay identico de cada uma.
powershell -NoProfile -ExecutionPolicy Bypass -File .\executar.ps1 -Cenario Negocio -Quantidade 10 -Valor 1000
# Mesma chave simultanea: exatamente 1 nova e 19 duplicatas.
powershell -NoProfile -ExecutionPolicy Bypass -File .\executar.ps1 -Cenario Duplicatas -Threads 20
# Carga moderada inicial; nao executada automaticamente.
powershell -NoProfile -ExecutionPolicy Bypass -File .\executar.ps1 -Cenario Carga -Threads 20 -Tenants 5 -Rps 50 -Duracao 60 -Html
```

`-JMeter` e `-JavaHome` permitem mudar os caminhos locais. Padroes:

- `C:\Developer\Programas\apache-jmeter-5.6.3\bin\jmeter.bat`
- `C:\Developer\Programas\jdk\openjdk\openjdk-21.0.2_windows-x64_bin\jdk-21.0.2`

Groovy 3.0.20 do JMeter 5.6.3 falhou com o Java 25 (`Unsupported class file major version 69`).
O wrapper usa Java 21 sem alterar o JDK da aplicacao. Use a GUI para editar e CLI para medir.
Para abrir a GUI com o Java correto, execute `abrir-jmeter-java21.cmd` desta pasta.
Feche antes a janela antiga do JMeter, preservando suas alteracoes, pois ela continua usando o JDK com que foi iniciada.
Os scripts Groovy sao resolvidos a partir da pasta do JMX, mesmo abrindo a GUI de outra pasta.

## Cenarios

| Grupo | Validacao | Propriedades para uso direto com -J |
|---|---|---|
| 00 Inicializacao | parametros e novos contadores/chaves em toda execucao | sempre ativo |
| 00 Tokens | HTTP 200 e access_token presente | tenants=20 |
| 01 Fumaca | 202, JSON, RECEIVED, chave, tracingId e timestamp | smoke_threads=1, smoke_loops=10 |
| 02 Carga | mesmas validacoes e limite por requisicao | load_threads=0, rps=500, duration=300, ramp=10, max_ms=100 |
| 03 Duplicatas | mesma chave/payload e banco-001 | storm_threads=0 |
| 04 Negocio | proposta nova seguida de replay identico | business_threads=0, business_requests=10, business_amount=1000.00 |
| 99 Veredito | 1 nova/N-1 duplicatas na tempestade; N novas/N replays e valor em negocio | automatico para 03/04 |

Os grupos sao sequenciais. Ao usar JMeter diretamente, desligue os demais por propriedades;
o wrapper ja faz isso. Negocio admite somente uma thread. O veredito cobre a entrada:
**HTTP 202 nao garante averbacao** e o plano nao consulta o estado final da proposta.

## Conferir no Grafana

Para reconciliar uma execucao sem depender do Grafana, ver a [captura SRE](../../docs/sre/captura-local.md).
Iniciar a aplicacao com `local-up.ps1 -SreCapture` e o wrapper com `-SreCapture` gera controle,
JTL enriquecido e resumo independente. O coletor le a Inbox local e o snapshot do worker;
nao presume que 202 e confirmacao. `-SreFingerprint` resolve aliases quando a API ja estiver
configurada em v2; nao ativa a funcionalidade.

1. Deixe a aplicacao enviar metricas por ao menos 30 s antes do teste.
2. Execute somente Negocio, sem outro trafego no mesmo ambiente/instancia.
3. Abra `Consignado - Negocio`, filtrando ambiente local, sua instancia, convenio inss e
   portabilidade false (ou os filtros usados no teste).
4. Padrao esperado: **10 novas, R$ 10.000 recebidos, ticket R$ 1.000**. Os 10 replays nao
   devem aumentar quantidade nem valor de negocio.
5. Aguarde a drenagem/coletas: confirmados + rejeitados devem atingir 10 ao finalizar tudo.
   Recebido e confirmado sao etapas da mesma proposta: nao somar esses valores.

Os totais Prometheus sao estimativas de `increase`, com extrapolacao entre coletas: compare
com tolerancia e confira o intervalo. O veredito JMeter confere os valores exatos na entrada.
O painel nao substitui conciliacao transacional. JTL inclui tenant/chave/valor/convenio/portabilidade,
sem token ou CPF; trate arquivos como dados de teste.

**Limite do mock:** propostas validas retornam AVERBADA. Caos/capacidade produzem 503 e retry,
nao rejeicao de negocio. Para testar rejeicao final e preciso evoluir explicitamente o contrato
do mock em outra mudanca. HTTP 400/401/403/429 na entrada tambem nao representa rejeicao no legado.

## Parametros do wrapper

| Parametro | Padrao | Uso |
|---|---|---|
| ApiHost, ApiPort, Protocol | 127.0.0.1, 8080, http | API |
| TokenHost, TokenPort, TokenProtocol | 127.0.0.1, 8082, http | emissor de tokens |
| Tenants | 20 | parceiros; threads de carga sao repartidas entre eles |
| Quantidade | 10 | propostas de Fumaca/Negocio |
| Valor, Convenio, Portabilidade | 1000, INSS, desligada | payload fixo de Negocio |
| Threads, Rps, Duracao, Rampa | 20, 50, 60, 10 | perfil de Carga; Threads tambem controla Duplicatas |
| MaxMs | 100 | limite por resposta da Carga, nao p95 agregado |
| Html | desligado | relatorio HTML |

A cota de 100 req/s por parceiro exige folga: considere min(Threads,Tenants), rajadas e
distribuicao desigual. A tempestade usa um unico parceiro: centenas de disparos podem causar
429 e invalidar o teste de idempotencia. Rps e alvo do timer, nao garantia de vazao.
O timer de carga e Constant Throughput Timer compartilhado pelo grupo, com conversao
de Rps para amostras/minuto. O timer anterior produziu uma rajada de recuperacao apos
o setUp lento; o teste isolado agora verifica tambem um limite superior de disparos.
Na mesma maquina, gerador, emuladores e servicos disputam CPU; isso nao homologa capacidade
de producao. Carga acima da drenagem do legado acumula fila.

## Verificacao isolada

`verificar-plano.groovy` sobe uma fixture HTTP em loopback/porta efemera e roda o JMeter real:
validacao, fumaca, negocio, duplicatas, carga pequena e dois erros deliberados (token ausente
e replay incorreto). Nao chama a aplicacao real, AWS ou Grafana; nao valida JWT, fila ou Worker.

```powershell
$java = 'C:\Developer\Programas\jdk\openjdk\openjdk-21.0.2_windows-x64_bin\jdk-21.0.2\bin\java.exe'
& $java -cp 'C:\Developer\Programas\apache-jmeter-5.6.3\lib\*' groovy.ui.GroovyMain .\verificar-plano.groovy $PWD.Path
```

Resultados da fixture ficam em resultados/; nao confundir com carga real.
Referencia: [componentes oficiais do JMeter](https://jmeter.apache.org/usermanual/component_reference.html).
