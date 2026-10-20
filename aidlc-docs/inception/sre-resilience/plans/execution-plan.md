# Plano AI-DLC: falha, detecção e recuperação da PoC

Revisão: 18/10/2026. Status: **ENCERRADO COMO PoC**. R0 e L1 foram concluídos; L2 preservou evidência parcial sem conciliação final; L3 comprovou proteção local e a lacuna do detector. R2-R4 e validações integradas remanescentes foram reclassificados como backlog para uma nova fase. Ver o [relatório de fechamento](../../../../docs/sre/fechamento-poc-sre-observabilidade.md), o [desenho R0](../../../construction/sre-r0/unit-design.md), o [resultado R0](../../../construction/sre-r0/validation.md) e a [validação R1](../../../construction/sre-r1/validation.md).

Restrição posterior do usuário: **não ligar a EC2 em 17/10/2026 (America/Sao_Paulo)**. Prosseguir apenas com preparação e validação locais nesta data; a continuidade do plano não revoga essa restrição.

Em 18/10, o usuário autorizou explicitamente ligar o ambiente e testar. O L1 foi executado com rastreamento e conciliação. Ao final dos exercícios, serviços locais, Docker e EC2 foram desligados; volumes e evidências foram preservados. A restrição acima permanece como registro histórico de 17/10.

## Decisões do workflow

| Etapa AI-DLC | Decisão | Resultado |
|---|---|---|
| Workspace Detection | Executada | Brownfield; aplicação e observabilidade existentes. |
| Reverse Engineering | Executada, incremental | [Sistema](../reverse-engineering/system-overview.md) e [interfaces](../reverse-engineering/api-and-dependencies.md). |
| Requirements Analysis | Executada | [Requisitos e critérios](../requirements/requirements.md). |
| User Stories | Dispensada | Melhoria operacional sem nova jornada de negócio. |
| Workflow Planning | Executada, Standard | Continuidade autorizada pelo usuário; manter gates de evidência por pacote. |
| Application Design / Units Generation | Executadas para R0 | Unidade incremental no mock e scripts, detalhada no desenho R0; demais pacotes ainda não detalhados. |
| Construction | Encerrada no escopo da PoC | R0 e L1 concluídos; L2 incompleto; L3 validado localmente; R2-R4 em backlog. |
| Validação operacional | Parcial, suficiente para o fechamento delimitado | L1 e falha do worker com evidência integrada; L2 sem conciliação final; L3 sem prova integrada do detector. |

A sequência planejada foi revisão do plano → R0 preparação → R1 legado → R2 DLQ → R3 carga → R4 incidente e documentação. A execução terminou após R1, com os resultados e limites registrados. As seções R2-R4 abaixo são backlog histórico e exigem novo planejamento antes de execução.

## Protocolo comum

Antes de cada execução, registrar ID do experimento, operador, início/corte UTC, configuração efetiva, versões e coorte. Usar dados sintéticos e guardar detalhes técnicos em diretório local ignorado pelo Git, sem segredos. O resumo sanitizado deve ser versionável.

Preflight obrigatório: serviços saudáveis, relógios coerentes, filas da coorte conhecidas, ledger e sessões preservados, métricas frescas, consulta de trace funcionando, rota de alerta conferida e rollback testado. Não limpar o histórico para obter gráficos favoráveis. Um ambiente sem coorte isolável ou com telemetria indisponível não está pronto.

Registrar t0 da falha, primeira evidência técnica, pending, firing, publicação SNS, ação de recuperação, primeira confirmação e drenagem final. Medir os intervalos, sem prometer MTTD/MTTR com base em uma única execução. Ausência de notificação deve separar falha do detector, agrupamento do Alertmanager e erro de publicação.

Limites iniciais propostos: uma execução por vez; teto de cinco minutos com falha ativa; teto de dez minutos para coleta de recuperação. Esses tetos não autorizam prolongar cenários curtos até a DLQ. Cada cenário abaixo tem restrições mais fortes. Ao atingir o corte, interromper a carga, restaurar o mock e declarar pendências, sem zerar as filas.

## R0 — Preparação e mecanismo de falha

**Dependência:** revisão do plano. **Componentes:** mock-legacy, local-env, scripts e captura SRE.

1. Conferir os parâmetros efetivos da [baseline](../reverse-engineering/api-and-dependencies.md), inclusive `maxReceiveCount`, timeouts e overrides.
2. Projetar a menor extensão que permita alterar a política de falha no processo do mock, usando os hooks de caos existentes. Ela deve estar desativada por padrão, limitada ao perfil de teste/loopback, protegida contra acesso indevido e ter expiração automática.
3. Escolher e documentar o mecanismo concreto antes do código: política controlável por harness no processo ou interface local protegida. Não presumir que um endpoint de caos já existe.
4. Criar comando de restauração idempotente e watchdog independente da conclusão do teste. Testar expiração inclusive se o controlador encerrar inesperadamente.
5. Preparar manifest da execução e coleta de JTL, estado das propostas, filas, métricas e referências de traces; excluir material sensível.

**Aceite:** ativar/restaurar sem reiniciar JVM, perder ledger ou trocar chaves; impedir ativação fora do perfil; provar expiração e falha fechada. Se isso exigir mudança ampla, reduzir a solução a harness integrado e revisar a possibilidade de demonstração visual antes de executar.

**Rollback:** desativar a política de teste e interromper o gerador. Se a restauração não for confirmada, abortar; não reiniciar processos como se isso demonstrasse recuperação com estado preservado.

## R1 — Indisponibilidade e lentidão do legado (prioridade)

**Dependência:** R0 aprovado tecnicamente. **Componentes:** mock, worker, testes de integração, regras/runbooks somente se a lacuna for confirmada.

| Cenário | Preparação / injeção proposta | Resultado que precisa ser observado |
|---|---|---|
| L1: 503 transitório | Cinco propostas novas; erro determinístico (`errorRate=1`) por até 2 s, restaurado antes de novo recebimento. Registrar tentativas efetivas. | Retry e confirmação das cinco identidades; zero DLQ inesperada. Abertura do circuito não é requisito com essa amostra. |
| L2: timeout ambíguo | Uma proposta; atraso de 4 s, acima do timeout de 3 s; remover atraso antes da repetição. | Verificar commit tardio no mock; replay com a mesma chave recupera um efeito, sem dupla contabilização. |
| L3: circuito | Coorte inicial de 20 propostas elegíveis, ampliada somente até 40 se necessário e se houver orçamento de tentativas; falha 503 determinística, até 15 s. | Observar transições, bloqueio de consumo em OPEN e sondagem em HALF_OPEN; restaurar antes de esgotamento inesperado. |

O primeiro ciclo de L1/L3 deve ser ensaiado em integração controlada: timing e concorrência podem consumir tentativas antes dos limites propostos. Interromper a injeção ao observar dois recebimentos de qualquer mensagem da coorte ou qualquer entrada inesperada na DLQ. Se isso impedir a hipótese de circuito, registrar a limitação e revisar o desenho, sem elevar silenciosamente o redrive.

O alerta atual `LegacyUnavailable` precisa de circuito OPEN, backlog e push fresco por dois minutos. L1/L3 não prometem dispará-lo. Mesmo numa falha longa, a sondagem automática após dez segundos pode reiniciar o período pendente. O trabalho inclui testar essa hipótese com séries temporais sintéticas e depois confirmar o comportamento integrado.

Se houver lacuna, propor um detector de indisponibilidade sustentada apoiado nas métricas realmente expostas: erros/tentativas ou sucessos do downstream em janela, trabalho pendente e frescor. Primeiro inventariar os nomes e denominadores; se faltar sinal, instrumentar o mínimo. Não criar uma expressão fictícia nem interpretar ausência de chamadas causada pelo circuito como sucesso.

**Gate de detecção:** testes de regras devem cobrir falha sustentada, intermitência OPEN/HALF_OPEN, recuperação, baixa amostra, ausência de tráfego e push stale/ausente. O detector deve ter condição de ação e runbook. Testar `promtool` antes de qualquer publicação. Uma prova integrada longa de firing fica condicionada a desenho que respeite o orçamento de tentativas; se a coorte for à DLQ, a demonstração passa a pertencer a R2 e não comprova recuperação automática.

**Aceite:** L1/L2 completos e L3 com resultado explícito, reconciliação por identidade/valor e cronologia de traces. Para encerrar a lacuna de detecção, exigir prova do detector escolhido e resolved; uma simulação sintética sozinha valida a regra, não a entrega real ao SNS.

**Rollback:** restaurar política do mock, parar novas entradas, acompanhar coorte e manter evidência de falhas/pendências. Reverter somente a alteração de regra deste pacote se ela introduzir regressão.

## R2 — DLQ observável e recuperação individual

**Dependência:** R1 e um mecanismo de inspeção seguro. **Componentes:** coleta local de filas, métricas, worker/scripts e runbooks.

1. Adicionar a menor coleta necessária para profundidade da DLQ e timestamp de amostra bem-sucedida. Distinguir disponível e em voo se a API permitir. Só incluir idade da mensagem mais antiga se houver fonte confiável e comportamento testado.
2. Propor warning para DLQ não vazia com coleta fresca; duração inicial candidata de um minuto, sujeita a testes e revisão. Ausência/staleness da coleta precisa de tratamento próprio. Isso é uma regra futura, não parte dos sete alertas atuais.
3. Enviar uma única proposta válida e provocar esgotamento deliberado. Teto de falha ativa: cinco minutos; se não houver redrive até o corte, restaurar e marcar INCOMPLETE.
4. Registrar causa, identidade canônica e estado. Restaurar o legado e redirecionar apenas a mensagem selecionada, preservando chave idempotente, envelope e contexto W3C.
5. Confirmar suporte do emulador antes de usar API automática de redrive. Na alternativa receive/send/delete, só excluir a origem depois do envio confirmado; se houver falha entre envio e exclusão, tolerar replay e demonstrar deduplicação.

**Aceite:** proposta na DLQ permanece pendente de recuperação técnica; depois do redrive há uma confirmação e um efeito no mock. Zero purga geral, duplicação financeira ou apagamento antes do envio confirmado. Testar falha de envio e execução repetida do redrive.

**Rollback:** suspender redrive e falha, manter mensagem/estado para investigação. Não deletar a evidência para limpar o painel. Validar alerta e resolved até SNS quando o ambiente estiver disponível; não afirmar entrega à SQS cloud.

## R3 — Carga progressiva e limites

**Dependência:** R1/R2 estabilizados, sem caos ativo e com filas da execução anterior conciliadas. **Componentes:** JMX, captura, relatório e dashboards existentes.

Degraus candidatos: 5 → 20 → 50 → 100 → 150 requisições/s, por 60 s cada, com drenagem e nova baseline entre degraus. O degrau de 180 é opcional após resultados estáveis. Conferir se o gerador atinge a taxa; registrar taxa oferecida e efetiva.

Usar pelo menos dois tenants nos degraus acima de 100 requisições/s, respeitando as cotas efetivas. Separar 429 esperado da política, rejeição de negócio, 503 de capacidade e erro inesperado. A referência de 180 TPS descreve o mock/configuração de um worker; não comprova limite global de várias instâncias.

Guardrails iniciais, a calibrar no primeiro ensaio: abortar degrau se backlog total ultrapassar 1.000 mensagens, espera da proposta mais antiga observada ultrapassar 60 s, aparecer DLQ inesperada ou a taxa de 5xx inesperado exceder 1% numa janela de 30 s com ao menos 20 respostas. Não aplicar a janela antes da amostra mínima. Falha de observabilidade também interrompe o aumento de carga.

Os guardrails são limites de laboratório, não novos SLOs. Medir latência, vazão, concorrência, backlog, idade observável, tentativas e tempo de drenagem. Um degrau só avança após reconciliação; não avançar apenas porque a média de latência ficou boa.

**Aceite:** relatório identifica o maior degrau sustentado dentro dos critérios e os pontos de saturação efetivamente encontrados; nenhum compromisso de atingir 180. Reutilizar o dashboard de Experimentos, sem somar execuções diferentes.

**Rollback:** interromper carga, manter processamento saudável, aguardar até dez minutos e registrar pendências remanescentes. Não aumentar concorrência/capacidade durante a mesma execução sem abrir novo experimento.

## R4 — Resposta a incidente e apresentação

**Dependência:** resultados de R1; incorporar R2/R3 somente quando concluídos. **Componentes:** runbooks e [roteiro da apresentação](../../../../docs/sre/apresentacao/README.md).

Repetir um cenário conhecido com um operador seguindo o runbook. Apresentar sintoma, alerta aplicável, investigação no dashboard e trace, ação, recuperação e notificação resolved até SNS. Registrar tempo de detecção, tempo até ação e tempo de recuperação separadamente; não chamar tempo humano de ação de latência do sistema.

Atualizar os documentos com hipótese, resultado real, limitações, comando de rollback e links de evidência sanitizados. Uma ação humana deve ser apresentada como tal. O recebimento por consumidor SQS e o reconhecimento humano não foram comprovados pelo publish SNS.

**Aceite:** outra pessoa consegue reproduzir o diagnóstico a partir do runbook, localizar uma proposta e distinguir recuperada, ainda pendente e enviada à DLQ. Encerrar gerador, processos e stack conforme autorização vigente no momento da execução; verificar estado final e preservar volumes/evidência.

## Melhoria opcional: acesso ao trace

Se localizar o trace por serviço/janela for um obstáculo, projetar campo técnico separado, por exemplo `X-OTel-Trace-Id`, e gravá-lo no JTL. Preservar `X-Trace-ID`/`tracingId` de negócio. Validar contexto da requisição atual, inclusive replays, sem vincular identidade de negócio ao trace. Esta melhoria pode entrar após R0; não bloqueia os cenários se a busca existente for suficiente.

## Riscos e controles de decisão

| Risco | Controle / condição de parada |
|---|---|
| Perda de ledger/chaves em reinício | Falhas no mesmo processo; bloquear ensaio que dependa de reinício para preservar estado. |
| Tentativas esgotarem antes do detector | Separar L1/L3 de R2; acompanhar recebimentos e DLQ; registrar lacuna de alerta. |
| Timeout com commit tardio | Ledger e replay com mesma chave; não reenviar como proposta nova. |
| Redrive duplicar mensagem | Send confirmado antes de delete; idempotência e teste de interrupção. |
| Métrica stale aparentar normalidade | Preflight/frescor explícitos; INCOMPLETE quando faltar cobertura. |
| Notificação automática indevida | Conferir labels/rota; usar `notification=sre`; não adicionar `action=trigger_agentic_loop`. |
| Carga esconder cota por tenant | Distribuir carga sintética e separar 429 de falha técnica. |
| Escopo ultrapassar a PoC | Reavaliar pacote antes de nova dependência, persistência ou permissão AWS. |

## Rastreabilidade e gates

| Requisito | Pacote | Evidência mínima |
|---|---|---|
| RF-01, RF-02, RNF-01/02/05 | R0 | Manifest, teste de isolamento, expiração e restauração. |
| RF-03, RF-04, RNF-03/04/06 | R1 | Integração retry/timeout, reconciliação e trace da tentativa/replay. |
| RF-05 | R1 | Testes temporais de regra e prova integrada das condições de detecção/recuperação. |
| RF-06 | R2 | Amostras da DLQ, testes de redrive interrompido/repetido e efeito único no mock. |
| RF-07 | R3 | JTL, carga atingida, séries frescas e relatório por degrau. |
| RF-08 | R4 | Linha do tempo, runbook usado e evidência firing/resolved até SNS. |
| RF-09, RNF-01/03/04 | Todos | Relatório sanitizado com cobertura, corte e estado final explícitos. |

Gate documental atual: links, coerência dos parâmetros e whitespace. Gate de código futuro: testes focados dos comportamentos alterados e checks obrigatórios do projeto; não reutilizar um build histórico como prova. Gate de regras: `promtool` e templates quando alterados. Gate operacional: preflight, rollback ensaiado e ativação explícita do ambiente. Publicar regras ou dashboards somente após validação e com resultado concreto para revisão.

## Resultado da execução e backlog

- [x] Revisar requisitos e selecionar o primeiro pacote: R0, autorizado pelo usuário.
- [x] R0: desenho técnico, implementação mínima e testes locais de reversão; preflight da jornada antes de R1.
- [x] R1: experimentos L1/L2/L3 encerrados com resultados explícitos.
  - [x] L1: recuperação real após erro transitório, com ressalva de latência no relatório SRE.
  - [x] L2: executado, mas classificado como incompleto por falta de conciliação final.
  - [x] L3: proteção comprovada localmente; validação integrada do detector permanece no backlog.
- [ ] R2: DLQ observável e redrive seguro.
- [ ] R3: carga progressiva com limites e conciliação.
- [ ] R4: incidente reproduzível, documentação e encerramento verificado.

Não há próximo passo ativo nesta PoC. Uma nova fase pode retomar a conciliação de L2, validar L3 de forma integrada e então avaliar R2-R4. O ambiente foi desligado ao final dos exercícios.

Atualização posterior de 18/10: **testes pausados a pedido do usuário**. L2 teve duas tentativas preservadas; na segunda, o Jaeger mostrou erro do cliente em 3 s, servidor executando por 4,1 s e retry 200. O fechamento da conciliação ficou pendente após erro de pós-processamento do harness. Retomar pelos artefatos existentes, sem criar nova coorte automaticamente; ver [relato L2](../../../../docs/sre/exercicio-falha-legado-l2.md).

Encerramento posterior em 18/10, 12:19: ambientes locais e Docker parados; EC2 confirmada `stopped`, com volume/evidências preservados. O estado operacional atual é desligado. A sessão em memória dos experimentos terminou; retomar pela revisão offline dos artefatos e não tentar reconstruir sua cobertura como se fosse a mesma sessão após reiniciar.

## Retomada local de L3 — 18/10/2026

Solicitada pelo usuario apos o encerramento do ambiente. Ensaio de protecao e recuperacao com HTTP real e fila em memoria, sem iniciar EC2. Desenho e limites em [L3](../../../construction/sre-r1/l3-design.md). A conciliacao offline do L2 continua pendente; o ensaio local nao fecha o gate integrado de R1. Nenhum input de negocio enviado a aplicacao nesta etapa.
