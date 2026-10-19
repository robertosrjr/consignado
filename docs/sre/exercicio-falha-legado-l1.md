# Exercício SRE L1: erro transitório do legado

Executado em 18/10/2026. Horários abaixo em America/Sao_Paulo (UTC−03:00). Aplicação e emuladores locais; observabilidade na EC2 existente. Escopo: L1 do [plano AI-DLC](../../aidlc-docs/inception/sre-resilience/plans/execution-plan.md), sem executar L2/L3, alterar alertas ou testar carga.

## Resultado da hipótese de recuperação

**PASS no L1:** cinco propostas novas de R$ 1.000,00 e cinco replays, todos com HTTP 202. As cinco identidades canônicas foram observadas como `CONFIRMED` na mesma sessão do worker. O mock registrou cinco efeitos únicos adicionais, uma falha de caos e nenhum replay adicional em seu ledger. As filas fast/standard e a DLQ locais ficaram vazias, incluindo mensagens em voo e atrasadas.

Uma proposta enfrentou 503 e foi recuperada por retry; as outras quatro chegaram ao legado depois da expiração da falha. Não afirmar que todas sofreram retry. O mock preservou ledger e chaves; nenhum processo foi reiniciado durante o experimento, nenhuma fila foi purgada e nenhuma métrica foi zerada.

A recuperação foi feita pelo retry existente, após a política temporária expirar no mock. Isso não comprova reinício automático, recuperação após perda de memória, exatamente uma vez financeiro em produção ou abertura do circuito. A coorte está abaixo do mínimo de vinte chamadas do circuito.

## Massa, aquecimento e tentativas

Antes do L1, um aquecimento separado enviou uma proposta de R$ 1.000,00 e um replay. Não pertence aos cinco itens do L1. Contadores cumulativos do dashboard de Negócio incluem esse aquecimento: nesta sessão há seis propostas novas, não cinco.

O helper [test-legacy-transient.ps1](../../applications/scripts/test-legacy-transient.ps1) gera uma cópia do JMX e scripts em diretório novo e insere a ativação apenas antes da primeira proposta nova. O plano JMeter original permanece preservado. A falha é erro determinístico, sem latência artificial, com duração máxima de dois segundos; não houve extensão do prazo para obter retry.

As duas primeiras tentativas do harness ficaram **INCOMPLETE**, sem envio de propostas e sem ativação da falha: uma por argumento decimal dividido pelo PowerShell; outra por whitespace introduzido em campos XML vazios. Ambas foram preservadas. A segunda também expôs contenção na leitura do snapshot no Windows; o helper agora usa compartilhamento de leitura/escrita/exclusão e retry limitado para erros de I/O. A tentativa `03` executou o cenário e passou.

## Linha do tempo

| Horário | Observação |
|---|---|
| 10:36–10:37 | Aquecimento: uma proposta nova e um replay, duas respostas 202. |
| 10:42:49 | Início do cliente L1, incluindo inicialização do JMeter. |
| 10:43:43,678 | Cliente iniciou a chamada para ativar a falha de dois segundos. |
| 10:43:44,116 | Controle confirmou falha ativa com aproximadamente 1.999 ms restantes. |
| 10:43:44,185 | Primeira proposta nova iniciou a chamada à API. |
| 10:43:44,791 | Worker iniciou a chamada ao legado que respondeu 503. |
| 10:43:46–10:43:47 | As outras quatro propostas foram confirmadas após a expiração. |
| 10:43:51,974 | Retry da proposta afetada; resposta 200, sem novo envio de proposta pelo cliente. |
| 10:43:52,003 | Última confirmação da coorte registrada pelo worker. |
| 10:43:52,526 | Cliente encerrado; controle já neutro antes do DELETE de restauração. |

A última confirmação ocorreu aproximadamente 8,3 s após o início da chamada de ativação. São tempos desta observação, não uma estimativa estatística de MTTR. A chamada 503 já em andamento terminou após a expiração; novas chamadas voltaram à baseline sem reiniciar o mock.

## Tracing e visualização

Cinco jornadas no Jaeger contêm `ingestion-api`, `dispatcher-local`, `worker` e `mock-legacy`: quatro com onze spans e uma com quatorze. A jornada com retry mostra 503 seguido de 200 no mesmo trace:

`055f4ce43f63e70323abc640456e4a0d`

Trace observado: `055f4ce43f63e70323abc640456e4a0d`. Após iniciar o ambiente, abra `http://<IP_ATUAL>:16686/trace/055f4ce43f63e70323abc640456e4a0d`; a retenção do Jaeger pode limitar sua disponibilidade.

Os pushes de API, worker e mock foram verificados no Prometheus com amostras recentes. Grafana e Jaeger responderam HTTP 200 na EC2 iniciada. Para inspecionar este episódio, selecionar o intervalo de 18/10/2026, aproximadamente 10:43–10:45, em vez de apenas os últimos minutos.

## Conciliação e metas de serviço

O PASS do L1 é separado da avaliação dos SLIs. Na amostra HTTP, nove das dez chamadas ficaram abaixo de 100 ms e de 200 ms; a primeira levou 314 ms. Isso fica abaixo das metas percentuais de latência do contrato existente. Não atribuir essa latência da entrada ao erro downstream sem investigação causal.

A primeira captura conciliada recusou cobertura de decisões: o snapshot envelheceu durante a inicialização do SDK. Foi preservada como INCOMPLETE. O coletor foi ajustado para ler o snapshot depois de criar o cliente DynamoDB, mantendo o limite de cinco segundos; a nova validação está registrada nos artefatos da unidade.

A segunda captura (`capture-02`) teve cobertura completa, sem inconsistências: cinco aceitas, cinco confirmadas, nenhuma aberta, R$ 5.000,00 recebidos/confirmados. Seu estado geral é **FAIL pelas metas de latência**, distinto do PASS funcional do L1. O relatório usa a política original `poc-v1-local`, sem prazos reduzidos. Foi publicado no grupo existente `sre-experiment/env/local/instance/desktop-psq0nq3`, sem criar dimensão por execução.

As metas standard têm janela/prazo de 2.700 s. Uma confirmação já observada não autoriza antecipar a maturação da coorte ou declarar SLO mensal cumprido. O valor nominal do L1 é R$ 5.000,00; a confirmação financeira aqui é exclusivamente do legado simulado.

## Artefatos e reprodução

Evidências técnicas locais, ignoradas pelo Git: `applications/.local/legacy-l1-20261018-01/`, `02/` e `03/`. A tentativa `03` contém JTL, resumo independente do JMeter, controle com hashes, ativação sem token, estados finais, filas, deltas de métricas e resumo do Jaeger. Não publicar dados brutos automaticamente.

Com o ambiente saudável, já aquecido, token do controle disponível na sessão e sem carga concorrente:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File applications/scripts/test-legacy-transient.ps1 `
  -OutputDirectory applications/.local/legacy-l1-nova-execucao
```

O diretório precisa ser novo. O helper recusa baseline não neutra, filas com trabalho e captura antiga. Faz restore em `finally`, além da expiração independente no mock. O [guia R0](preparacao-falhas-legado.md) explica ativação do perfil e token, sem armazenar credenciais no repositório.

O ambiente permanece ativo a pedido do usuário. O exercício curto não comprova disparo de `LegacyUnavailable`, publicação firing/resolved no SNS ou entrega na SQS cloud. Próximo cenário do plano: L2, timeout com possível commit tardio; depois L3 e avaliação da lacuna de detecção.
