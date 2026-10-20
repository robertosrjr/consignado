# Exercício SRE L2: timeout e possível commit tardio

Data: 18/10/2026. Estado desta revisão: **PAUSADO a pedido do usuário; sequência de timeout e execução tardia observada, fechamento da conciliação pendente**. O [desenho](../../aidlc-docs/construction/sre-r1/l2-design.md) define hipótese e critérios; este documento registra os resultados observados, inclusive falhas do protocolo.

## Protocolo

Uma proposta nova de R$ 1.000 e um replay da API, com a mesma identidade. Configuração temporária do mock: latência de 4.000 ms, taxa de erro zero e TTL de 2.000 ms. Timeout do worker: 3 s; retry base: 5 s. A configuração expira antes do retry; uma chamada que capturou o atraso pode continuar após a expiração.

O sucesso exige demonstrar erro do primeiro cliente, execução do servidor até o commit tardio, um efeito único, retry que recupera esse efeito e confirmação da mesma proposta. Apenas duas respostas 202 e ausência de duplicidade não provam essa hipótese. Não confundir replay idempotente do ledger com segundo efeito financeiro.

## Tentativa 01 — FAIL do cenário

Evidências: `applications/.local/legacy-l2-20261018-01/`, ignoradas pelo Git. Horários abaixo em America/Sao_Paulo.

| Evento | Resultado |
|---|---|
| Ativação observada | 11:51:39,730; atraso 4 s, TTL restante aproximadamente 1.999 ms. |
| API | Uma nova e um replay, ambos 202; latências de 3.027 ms e 196 ms. |
| Primeira chamada do worker | Início 11:51:42,822, já depois da expiração; erro após aproximadamente 4,26 s. |
| Chamada posterior | Início 11:51:53,281; HTTP 200, duração aproximada de 81 ms. |
| Mock | Um efeito único adicional e zero replay no ledger. |
| Worker | Identidade canônica confirmada às 11:51:53,362. |
| Filas locais | Fast, standard e DLQ vazias, incluindo mensagens invisíveis/atrasadas. |
| Controle ao final | Neutro antes do restore explícito; nenhuma reinicialização ou purga. |

Trace observado: `5325e5ade9c8dfe9d2fef3e0bbc9a834`. Possui spans de ingestion-api, worker e mock-legacy; o dispatcher não apareceu na consulta desta tentativa. O primeiro cliente tem erro; somente a chamada posterior tem atendimento correspondente observado no mock. Não atribuir o primeiro erro à latência injetada, nem afirmar commit tardio. A proposta foi recuperada, mas o protocolo L2 não foi exercitado.

A inicialização do gerador e outras consultas locais demoraram vários minutos. Uma observação intermediária também teve grande intervalo entre corte do snapshot e leitura de métricas; esses instantes não constituem um corte atômico e não devem ser usados para inferir a ordem de commit. A cronologia dos spans e a expiração comprovada são a base desta conclusão.

## Repetição controlada

A tentativa `legacy-l2-20261018-02` usa nova identidade e diretório, preservando o ledger anterior. O gerador passa a ter heap 128–512 MB e processo filho próprio com teto de 180 s. O watchdog encerra somente esse processo se o prazo for excedido; o restore permanece em finally e o mock conserva expiração independente.

Os parâmetros da aplicação e da falha permaneceram iguais. O JMeter concluiu uma proposta e um replay, ambos 202, com todas as assertions bem-sucedidas. O harness falhou depois do cliente, porque `Process.ExitCode` retornou null no Windows; seu `result.json` permanece INCOMPLETE. A correção mantém o handle do processo aberto; não converter o resultado bruto em PASS nem reenviar a proposta para compensar esse erro.

O trace `5cf549257710db75df51aac27a25df16` contém quatorze spans, os quatro serviços e nenhuma referência interna não resolvida:

| Evento | Horário / duração observada |
|---|---|
| Primeiro cliente do worker | Início 12:05:38,763; erro após 3,001 s. |
| Primeiro servidor mock | Início 12:05:39,368; execução por 4,111 s, terminando depois do cliente. |
| Retry do worker | Início 12:05:46,839; HTTP 200 em aproximadamente 24 ms. |

Os contadores globais do mock naquele corte registraram oito efeitos únicos e um replay de ledger, incluindo coortes anteriores. Essa é evidência complementar à cronologia; a conciliação por identidade da tentativa02 deve ser fechada antes do resultado final. Trace observado: `5cf549257710db75df51aac27a25df16`. Após iniciar o ambiente, abra `http://<IP_ATUAL>:16686/trace/5cf549257710db75df51aac27a25df16`; a retenção do Jaeger pode limitar sua disponibilidade.

O usuário pediu pausa após essas observações. A falha foi restaurada e o ambiente permanece ligado. Na retomada, conferir os artefatos existentes (`requests.jtl`, `client-summary.json`, `postprocess-recovery.json` e `sre-control-recovered.json`, se concluídos), validar sessão/cobertura, gerar a captura conciliada e concluir o relatório. Não reenviar a massa automaticamente. O painel de Experimentos ainda conserva o relatório publicado do L1; não foi publicado um resultado final do L2.

Encerramento posterior solicitado pelo usuário: em 18/10/2026 às 12:19, serviços locais, containers e Docker foram parados e a EC2 foi confirmada `stopped`. Volume e arquivos de evidência foram preservados; estados voláteis dos emuladores/worker não são recuperáveis pelo simples restart. A preparação do controle reconciliado foi concluída antes do encerramento, preservando o `result.json` original INCOMPLETE; conferir esses artefatos offline na retomada, sem esperar a sessão antiga em memória.

## Reprodução

Com o ambiente saudável, sem outra carga, captura fresca e token do controle disponível na sessão:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File applications/scripts/test-legacy-transient.ps1 `
  -Scenario L2 -OutputDirectory applications/.local/legacy-l2-nova-execucao
```

O diretório precisa ser novo. Ver [preparação R0](preparacao-falhas-legado.md) para ativação do controle e [L1](exercicio-falha-legado-l1.md) para a evidência já concluída. Nenhum resultado desta rodada comprova SLA mensal ou efeito financeiro em produção.
