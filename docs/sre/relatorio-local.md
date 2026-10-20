# Relatório local SRE

Ferramenta Java do módulo `local-env`, sem serviço adicional ou dependência nova. Implementa o [contrato de experimento v1](contrato-medicao.md) e gera JSON, Markdown e métricas Prometheus em `report.prom`. Não altera o plano JMeter nem inicia aplicação, EC2, Docker ou notificações. Gerar o relatório não envia métricas automaticamente.

## Executar a demonstração

Pré-requisitos: JDK 25 e dependências Maven já resolvidas. Na pasta `applications`, preparar as classes e bibliotecas:

```powershell
mvn -pl local-env -am package -DskipTests
```

Esse comando empacota; não comprova testes aprovados. A demonstração usa somente dados sintéticos versionados, sem tráfego HTTP. A partir da raiz do repositório:

```powershell
New-Item -ItemType Directory -Force -Path applications/.local | Out-Null
powershell.exe -NoProfile -ExecutionPolicy Bypass -File applications/scripts/sre-report.ps1 `
  -Manifest applications/local-env/src/test/resources/sre/manifest.json `
  -Evidence applications/local-env/src/test/resources/sre/evidence.json `
  -OutputDirectory applications/.local/sre-demo-001
$LASTEXITCODE
```

O diretório final deve ser novo e seu pai deve existir. Resultados anteriores nunca são sobrescritos. Para repetir, escolher outro sufixo, como `sre-demo-002`.

O código esperado nesta fixture é **3 (`FAIL`)**: das três propostas aceitas, apenas uma terminou dentro do prazo de 120 s, outra terminou tarde e outra continua aberta. Os arquivos `report.json` e `report.md` explicam o resultado; o JSON inclui SHA-256 dos dois arquivos de entrada, contagens, razões e consumo de orçamento.

## Arquivos de entrada

O [manifesto de exemplo](../../applications/local-env/src/test/resources/sre/manifest.json) contém janela, corte, número esperado de tentativas e política explícita de prazos/metas. A [evidência de exemplo](../../applications/local-env/src/test/resources/sre/evidence.json) contém três fontes normalizadas.

| Fonte | Campos por registro | Origem exigida para uso real |
|---|---|---|
| `attempts` | `id`, `canonicalId` ou null, `startedAt`, `elapsedMillis`, `status`, `eligible`, `exclusion`, `responseKind`. | Cliente/massa/JTL e correlação da resposta, incluindo falhas sem resposta. |
| `acceptances` | `canonicalId`, `receivedAt`, `lane`, `amountCents`. | Snapshot/evidência de aceite original na Inbox, não somente corpo 202. |
| `decisions` | `canonicalId`, `decidedAt`, `outcome`. | Estado terminal/evidência real do worker e legado; ausência só é conclusiva com fonte completa. |

Todos os campos do schema são obrigatórios, mesmo quando `canonicalId` é explicitamente null. O parser rejeita campos desconhecidos e chaves JSON duplicadas. `schemaVersion` é 1 nos dois arquivos. Datas devem usar ISO-8601 UTC, valores monetários são centavos inteiros e identificadores são técnicos, sem dados pessoais.

O JTL **não é entrada direta desta CLI**. O novo [coletor local](captura-local.md) reconcilia o CSV com Inbox e snapshot do worker e gera as entradas normalizadas. Não marcar `coverage=true` apenas porque o JTL foi gravado: a captura verifica sessão, hashes, contagens e corte.

`responseKind` aceita `RECEIVED`, `DUPLICATE_ACCEPTED` ou `NONE`; `status: 0` representa erro de conexão/timeout sem HTTP. Não usar `success` do JTL para transformar uma falha de assertion de duração em falha de aceite: A e B são calculados separadamente.

## Resultados e segurança dos artefatos

- Códigos: 0 pass; 2 incomplete; 3 fail; 4 observing/no-events.
- `INCOMPLETE` prevalece quando a evidência não permite avaliação confiável; entrada ausente/inválida produz relatório genérico sem imprimir conteúdo, IDs ou stack trace.
- Diretório de saída existente ou indisponível retorna 2, sem alterar relatórios anteriores. Se nem o disco permite escrita, o código de processo é o sinal de falha.
- O relatório contém agregados, sem listar identificadores individuais. Os arquivos de entrada podem conter identificadores técnicos e valores; tratá-los como artefatos de acesso controlado.
- Guardar resultados reais em `applications/.local/`, já ignorada pelo Git. Somente fixtures sintéticas, código e contrato ficam versionados.
- A ferramenta mantém a evidência em memória; é destinada ao primeiro ciclo de experimentos controlados. Milhões de registros exigem futura leitura por lotes/streaming e medição do consumo de memória.

## Testes

A extensão Prometheus possui testes focados do avaliador, CLI e exportador. A evolução dos cartões conciliados inclui verificação de centavos, replay, rejeição comercial, falha técnica e supressão de totais com cobertura incompleta. Consultar os relatórios da execução antes de declarar o build aprovado.

Validação local de 17/10/2026: **14 testes focados aprovados** (10 do avaliador e 4 da CLI), mais execução do wrapper PowerShell com a fixture. A demonstração gerou os dois relatórios e retornou o `FAIL` esperado: aceite 100%, C_fast 33,33% e uma pendência vencida. Essa evidência é sintética; não representa uma execução de carga ou a suíte completa do projeto.

Em `applications`, para repetir somente os testes desse incremento:

```powershell
mvn -o -pl local-env -am test `
  '-Dtest=SreReportTest,SreReportMainTest,SreReportPrometheusTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

Remover `-o` se as dependências ainda não estiverem no cache. Os testes verificam pendências, atrasos, replay, timeout com aceite persistido, rejeição comercial, erro técnico, prazo exato, ausência de eventos, falta de cobertura, conflitos e parsing da CLI. Não são homologação de carga nem validação no Grafana.

## Integração com Grafana

Os [dois novos dashboards SRE](../../applications/observability/grafana/dashboards/README.md) estão versionados e foram sincronizados na EC2 em 17/10/2026. O painel `Consignado - SRE - Experimentos` usa somente **gauges do último relatório**; não aplicar `rate()` ou `increase()` a esses snapshots.

`report.prom` expõe famílias `sre_experiment_indicator_good`, `indicator_bad`, `indicator_eligible`, `indicator_target`, `indicator_ratio` e `indicator_budget_consumed_ratio` (todas com prefixo `sre_experiment_`), com a dimensão limitada `sli`. Não usa ID de execução, proposta ou fingerprint como label.

`sre_experiment_report_status` usa 0 pass, 1 fail, 2 incomplete, 3 observing e 4 no-events — codificação diferente do exit code da CLI. `coverage_complete` indica cobertura declarada/consistência; `observed_at_seconds` registra o corte da evidência. Com cobertura incompleta, as razões/totais não são exportados. Com denominador zero, a razão daquela classe fica ausente.

O envio é explícito, pelo comando abaixo. O exemplo usa a fixture; uma execução real deve apontar para o diretório do seu próprio relatório e usar ambiente/origem correspondentes:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File applications/scripts/sre-report-push.ps1 `
  -ReportDirectory applications/.local/sre-demo-001 `
  -PushgatewayUrl 'http://<ENDERECO_DO_PUSHGATEWAY>:9091' `
  -Environment fixture `
  -Instance sre-demo
```

Usar ambiente/origem próprios para dados sintéticos. O envio usa PUT no grupo fixo `job=sre-experiment/env=<ambiente>/instance=<origem>`, substituindo o snapshot e removendo razões anteriores quando o novo relatório é incompleto. Não criar um grupo por execução. O comando depende da coleta existente do Pushgateway com `honor_labels: true`.

A instância desse painel representa a origem estável do relatório, não necessariamente uma JVM. A data exibida é o corte da evidência, não a data do push. Série persistida não indica experimento em andamento ou SLO mensal vigente. Os painéis continuam sem dados enquanto não houver publicação explícita.

## Totais de negócio conciliados por execução

O bloco **Negócio — totais conciliados da execução** do painel SRE — Experimentos apresenta:

- Quantidades de propostas únicas recebidas, confirmadas, rejeitadas e pendentes.
- Valores recebidos, confirmados, rejeitados e pendentes, com duas casas decimais em reais.
- Tickets médios recebido e confirmado; denominador zero deixa o respectivo cartão sem dados.
- Início e fim da janela de aceites da execução, além do corte de observação já exibido no topo.

O exportador acrescenta `sre_experiment_received_cents`, `confirmed_cents`, `rejected_cents`
e `open_cents`, todos com prefixo `sre_experiment_` e tipo gauge. Os valores de entrada são
somados em centavos inteiros pelo avaliador. O Grafana divide os centavos por 100 para exibir
reais e divide o valor pela quantidade correspondente para calcular cada ticket. Não há
extrapolação temporal. Reenvios não somam novamente o valor da proposta original.

Os cartões leem o snapshot diretamente e exigem cobertura completa. Atualizar a tela não
recalcula o relatório. Alterar a janela do Grafana não transforma os cartões em totais dos
últimos dez minutos. Eles representam somente a execução fornecida, com as decisões conhecidas
no corte observado; confirmação tardia aparece no valor confirmado, preservando a violação do
prazo no SLI C. Erros técnicos permanecem pendentes.

Para reexportar um relatório anterior com os novos gauges, executar `sre-report.ps1` com os
mesmos `manifest.json` e `evidence.json`, usando um diretório de saída novo, e depois publicar
esse diretório com `sre-report-push.ps1`. Isso preserva o corte original e os hashes das fontes;
não é uma nova captura nem uma avaliação de tráfego posterior. Para atualizar as decisões,
fazer uma nova [coleta das fontes](captura-local.md) enquanto a sessão original estiver válida.

O painel operacional `Consignado - Negocio` continua apresentando estimativas no período.
Persistência das decisões e agregação contínua permanecem no plano de evolução.

Validação desta extensão em 17/10/2026: **17 testes focados aprovados** (10 do avaliador, quatro
da CLI e três do exportador), incluindo dinheiro em centavos, replay, rejeição comercial e
falha técnica. Na EC2, as 38 expressões do dashboard atualizado foram validadas no Prometheus,
os dez cartões de negócio foram comparados com o relatório e o Grafana confirmou 34 painéis.
Também foi verificado que os cartões suprimem resultados quando a cobertura exigida não é
atendida e que o valor recebido permanece estável entre consultas.

Foi reexportado o experimento real de 17:10:11 a 17:10:31 (America/Sao_Paulo), com o corte
original de 17:11:45: três propostas únicas recebidas e confirmadas, R$ 3.000,00 recebidos e
confirmados, nenhuma rejeitada ou pendente, tickets de R$ 1.000,00. Os hashes do manifesto e
das evidências foram preservados; não foi gerado tráfego novo para esta extensão. O resultado
geral `FAIL` por latência permaneceu inalterado. A publicação anterior do dashboard foi
guardada em backup fora da pasta do provider. Os caminhos dos artefatos locais constam em
`applications/.local/sre-business-publication.json`, ignorado pelo Git.

## Obstáculos e pendências

A [captura local](captura-local.md) integra as três fontes no escopo controlado documentado. Persistência do worker, avaliação periódica e métricas operacionais dependem dos próximos incrementos. O resultado não comprova durabilidade após reinício, exclusão entre réplicas, efeito financeiro único ou SLA mensal.
