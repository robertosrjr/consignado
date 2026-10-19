# Captura local: JMeter → Inbox → worker → relatório SRE

Esta integração coleta automaticamente as evidências das tentativas HTTP, dos aceites no DynamoDB Local e do estado observado do worker. Usa o mesmo avaliador do [contrato v1](contrato-medicao.md), sem publicar no Grafana, Pushgateway ou AWS.

O escopo é um experimento controlado, isolado, com **até 10.000 tentativas**, uma JVM de worker e os endpoints/tabelas padrão do ambiente local. Não é inventário global de propostas ou projeção durável de produção.

## Preparar a aplicação

Na pasta `applications`, gerar os artefatos atualizados:

```powershell
mvn -pl worker,local-env,ingestion-api,mock-legacy -am package -DskipTests
```

Esse comando empacota, sem homologar testes. O JDK da aplicação é o declarado no parent Maven; o wrapper do JMeter usa Java 21.

Iniciar o ambiente com captura habilitada. O comando abaixo desliga explicitamente o push, mesmo se houver endereço de uma execução anterior salvo:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "& '.\scripts\local-up.ps1' -SreCapture -PushgatewayAddress '' -OtlpTracesEndpoint ''"
```

Se o ambiente já estiver em execução sem captura, planejar seu encerramento/reinício antes de iniciar outro. Não iniciar uma segunda cópia sobre as mesmas portas. A instrumentação não habilita Business Fingerprint nem modifica a política de negócio.

`-SreCapture` configura somente o worker para escrever `applications/.local/sre/worker-snapshot.json`. Sem esse parâmetro, a exportação não é habilitada pelo iniciador. A propriedade equivalente é `worker.sre.capture-file`, sem valor padrão.

O snapshot é substituído atomicamente a cada segundo. Contém UUID da sessão, início, corte de observação e estados técnicos, sem CPF, payload, tenant, valor ou identificador de averbação. Uma falha de escrita deixa o corte antigo; o coletor não o trata como dado novo.

## Executar o JMeter

A partir da raiz do repositório:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File jMetter/consignado/executar.ps1 `
  -Cenario Negocio -Quantidade 3 -Valor 1000 -SreCapture
```

O cenário produz três propostas novas e três replays. O wrapper imprime o caminho de `<execucao>.sre-control.json`, junto do JTL e dos logs em `jMetter/consignado/resultados/`, ignorados pelo Git.

O JTL grava identidade técnica, identidade canônica retornada, resultado HTTP e elegibilidade da massa. Os cenários atuais geram propostas elegíveis; um 429 não é excluído automaticamente. O resultado `success` do JTL não substitui A/B: uma assertion de duração pode falhar com um 202 de aceite correto.

O encerramento do plano grava uma contagem independente em `<execucao>.sre-summary.json`. O controle guarda as contagens esperadas e hashes SHA-256 do JTL/resumo. Ausência, alteração ou truncamento impedem aprovação. Para carga de duração variável, a comparação usa também o contador independente do plano, não apenas o número de linhas lidas do CSV.

Mesmo que o wrapper termine com falha por uma assertion HTTP, pode haver controle de captura válido. Usar o relatório para separar falha observada de perda de evidência; não corrigir o JTL para deixar o teste verde.

Se a aplicação **já estiver configurada** no modo Business Fingerprint v2, acrescentar `-SreFingerprint`. Essa opção faz o coletor resolver os aliases `HEADER#tenant#chave` na tabela `ProposalDeduplication`; não ativa a funcionalidade na API nem cria recursos. Ver [implementação v2](../business-fingerprint-v2-implementacao.md).

## Coletar e avaliar

Manter os processos no ar e usar o arquivo de controle impresso pelo wrapper:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File applications/scripts/sre-collect.ps1 `
  -Control 'jMetter/consignado/resultados/<execucao>.sre-control.json' `
  -Policy jMetter/consignado/sre-policy.json `
  -OutputDirectory applications/.local/sre/captura-001
```

O diretório final deve ser novo; seu pai deve existir. São produzidos:

- `manifest.json`: política, janela real da execução e corte real do snapshot.
- `evidence.json`: tentativas, aceites e decisões normalizados para o avaliador.
- `capture.json`: identificação técnica da fonte, hash e inconsistências categóricas.
- `report/report.json`, `report.md` e `report.prom`: resultado agregado e métricas de snapshot.

Falha de fonte/schema/hash produz `INCOMPLETE`, código 2 e artefato de erro sem conteúdo sensível. Os demais códigos seguem o [guia do relatório](relatorio-local.md). Saídas anteriores nunca são sobrescritas.

O coletor consulta **somente `127.0.0.1:8000`**, com credenciais fictícias e leitura consistente das identidades tentadas, sem scan geral ou descriptografia. Não usa o profile AWS da estação. Os campos de aceite/valor vêm da Inbox, não do corpo HTTP nem de `increase()` do Prometheus.

A lane e o instante de roteamento são persistidos pelo dispatcher ao marcar `DISPATCHED`. O coletor não infere prioridade pelo valor nem fixa o limiar de score. Registros antigos despachados sem esses metadados não fornecem cobertura suficiente; não inventar uma classe para eles.

O worker fornece um corte consistente dos estados em memória e o instante de gravação terminal. `CONFIRMED` vira confirmação; `FAILED` permanece falha técnica/não resolvida nesta integração, pois o estado atual não discrimina uma rejeição comercial fundamentada. Não presumir rejeição comercial a partir do nome do estado.

O controle e o coletor verificam a sessão do emulador pelo PID/início e a sessão do worker pelo UUID/início do snapshot. Exigem corte posterior ao fim da execução e amostra recente, com até cinco segundos de idade. Não é permitido usar um `asOf` futuro para antecipar o vencimento.

## Aguardar os prazos sem alterar os resultados

A [política versionada](../../jMetter/consignado/sre-policy.json) explicita 120 s para fast, 2700 s para standard e 300 s para não roteadas. Esses números são experimentais, sem SLA externo.

O relatório pode ficar `OBSERVING` antes de vencerem os prazos, mesmo com propostas já confirmadas. Para reavaliar, aguardar o tempo real e executar novamente o coletor com o mesmo controle e outro diretório de saída, sem reiniciar os processos. A conclusão tardia continua ruim para o prazo original.

Para um teste técnico curto, usar uma **cópia explícita da política** no diretório de resultados, com prazos menores e versão distinta. Isso testa o mecanismo de captura; não homologa os objetivos originais nem deve ser apresentado como SLO de produção.

## Evidências locais de 17/10/2026

Foram executados 48 testes focados: 27 do parser/coletor/avaliador/exportador Prometheus,
14 do dispatcher e sete do repositório/snapshot do worker, sem falhas. As integrações
`SreCaptureInboxIT` e `DispatcherEndToEndIT` também passaram com DynamoDB Local e filas locais.
Isso não representa execução da suíte completa.

Além dos testes automatizados, API, dispatcher, filas, worker, mock de legado e JMeter foram
executados localmente, sem push ou exportação OTLP. A política técnica `validation-short-5s`
usou cinco segundos para todas as classes, preservando as metas do arquivo padrão. Foram
observados os seguintes resultados, sem antecipar o corte de observação:

| Experimento | Tentativas / aceites HTTP bons | Propostas únicas | Confirmadas | Pendentes vencidas | Valor recebido / confirmado | Decisão standard no prazo |
|---|---|---|---|---|---|---|
| Legado disponível, três novas e três replays | 6 / 6 | 3 | 3 | 0 | R$ 3.000,00 / R$ 3.000,00 | 3/3, 100% |
| Mock de legado parado, duas novas e dois replays | 4 / 4 | 2 | 0 | 2 | R$ 2.000,00 / R$ 0,00 | 0/2, 0% |

Ambas as coletas tiveram cobertura completa. O primeiro relatório teve estado geral `FAIL`
por latência: cinco das seis requisições atenderam tanto 100 ms quanto 200 ms, abaixo das
metas de 95%/99%. O segundo comprovou que HTTP 202 não representa decisão concluída e que
pendências entram no denominador de prazo. Nenhuma meta foi relaxada para obter aprovação.

Após encerrar os serviços, uma nova coleta do mesmo controle retornou `INCOMPLETE` e código
de saída 2. A falta de fontes ativas não foi apresentada como ausência de demanda ou sucesso.

Os controles/JTLs ficaram em `jMetter/consignado/resultados/`; a política curta e os relatórios
ficaram em `applications/.local/sre/`, com referências em
`applications/.local/sre-validation-artifacts.json`. Esses artefatos locais são ignorados pelo
Git, podem conter identificadores técnicos e não devem acompanhar o artigo sem revisão.
Os serviços temporários foram encerrados após as capturas; não houve sincronização remota.

## Limites e cuidados

- A coorte é a massa de identidades tentadas pelo experimento e seus aceites originais dentro da janela. Tráfego concorrente de outra origem não é coberto; executar isoladamente.
- O estado do worker e o emulador continuam em memória. Capturar antes do desligamento; arquivos de snapshot não substituem persistência, exclusão entre réplicas ou recuperação após reinício.
- O snapshot precisa de exclusão breve com as escritas do repositório para obter um corte consistente. Há custo de serialização/IO; habilitar para testes controlados, sem usar esse modo para homologar alta vazão.
- A captura resolve aliases v2, mas não prova por si só sua regra de equivalência, retenção ou ausência de efeitos duplicados no legado.
- Cancelamento, alteração de valor, múltiplas réplicas, mudanças de classe/política e leitura em escala exigem as etapas seguintes do plano.
- Não há sincronização automática dos dashboards ou envio de métricas. Uma futura publicação deve usar `report/` como diretório do relatório para o [envio explícito](relatorio-local.md).

Referência do formato JTL e timestamps: [propriedades oficiais do JMeter](https://jmeter.apache.org/usermanual/properties_reference.html). Os arquivos reais devem permanecer nos diretórios de resultados ignorados pelo Git.
