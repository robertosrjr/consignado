# L3 — proteção e recuperação com circuit breaker

Data: 18/10/2026. **Estado: ensaio local de proteção e recuperação aprovado; lacuna do detector comprovada por testes de regras.** A demonstração com aplicação completa, SQS, Grafana, Jaeger e publicação SNS permanece pendente. A EC2 não foi iniciada nesta etapa. O fechamento offline do L2 continua pendente.

## O que estamos demonstrando

O circuito protege o legado quando há falhas. Em CLOSED, aceita chamadas. Em OPEN, o loop do worker deixa de retirar mensagens da fila. Após a espera, HALF_OPEN permite sondagem, com um processamento em voo por vez. Cinco chamadas bem-sucedidas permitem fechar o circuito e retomar o restante.

Não basta observar OPEN no gráfico: precisamos verificar que a proteção não esgota tentativas enquanto bloqueia o consumo e que as mesmas identidades são recuperadas depois. Falhas e recuperação também precisam ser avaliadas separadamente da capacidade de alertar.

## Resultado local

O [teste L3](../../applications/worker/src/test/java/br/com/consignado/platform/worker/e2e/LegacyCircuitRecoveryTest.java) usa servidor HTTP real em loopback, cliente do legado, Resilience4j, adaptador de disponibilidade, loop de despacho e fila em memória. Tem processamento síncrono e limitado a uma chamada por vez. Não usa a ingestão, Inbox, SQS nem persistência do caso de uso completo.

| Verificação | Resultado |
|---|---|
| Coorte | 20 identidades sintéticas, R$ 1.000 por chamada. |
| Abertura | Após 20 chamadas com HTTP 503, circuito OPEN. |
| Proteção | 100 novos passos do loop em OPEN não fizeram despacho nem chamada HTTP adicional. |
| Tentativas | Mensagens ainda estavam na segunda entrega ao inspecionar a fila após OPEN; não houve consumo adicional pelo loop bloqueado. |
| Sondagem automática | HALF_OPEN observado após a espera configurada de 10 s, sem forçar a transição. |
| Fechamento | Quatro sucessos mantiveram HALF_OPEN; o quinto fechou o circuito. |
| Recuperação | 20 acknowledgements e as mesmas 20 identidades no servidor, sem replay de sucesso. |
| Integridade da fila de teste | Zero DLQ e nenhuma mensagem restante. |

A inspeção de recebimentos no teste faz uma retirada e devolução por mensagem. Ela consome uma entrega da fila em memória antes da recuperação; não deve ser copiada como procedimento de inspeção de SQS. O teste não prova efeito financeiro único em produção nem relatório conciliado de valores/SLIs.

Validação inicial: 28 testes focados aprovados, sem falhas/erros. Depois, a versão final do L3 com transição automática foi executada novamente: um teste aprovado em 15,35 s, com BUILD SUCCESS. Formatação e Enforcer passaram no ciclo. Não foi um `mvn verify` completo ou uma validação de cobertura de toda a aplicação.

Evidências preservadas em `applications/.local/legacy-l3-20261018-local/`: logs, XML JUnit, resumo com hash do teste e resultado da validação Prometheus. Os arquivos são ignorados pelo Git.

Logs originais, também ignorados pelo Git:

- `applications/.local/l3-focused-tests.log`: 28 testes, incluindo a versão inicial com transição controlada.
- `applications/.local/l3-automatic-tests.log`: versão final com temporizador automático.
- Relatórios JUnit em `applications/worker/target/surefire-reports/` são substituídos em novas execuções.

Para repetir a versão final, a partir de `applications`, com Java 25 e Maven:

```powershell
mvn -B -ntp -pl worker -am test `
  '-Dtest=LegacyCircuitRecoveryTest,LegacyHttpAdapterTest,CircuitBreakerAvailabilityAdapterTest,ProposalDispatchLoopTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false'
```

## O alerta acompanha esse ciclo?

A regra atual `ConsignadoLegacyUnavailable` exige simultaneamente:

- circuito no estado `2` (OPEN);
- fila pendente;
- push do worker com menos de 120 s;
- condição verdadeira continuamente durante 2 min.

O circuito começa a sondar após 10 s. Se alternar OPEN/HALF_OPEN antes de completar os 2 min, a regra pode perder sua condição pendente repetidamente. Portanto, indisponibilidade sustentada não implica disparo desse detector. Além disso, uma coleta de 15 s pode perder estados curtos: complementar o gráfico com eventos/logs e traces.

Foram preparados [cinco cenários de regra](../../infrastructure/Configuration/observability/config/prometheus/tests/consignado-l3.test.yml), com 15 verificações: OPEN contínuo com disparo e resolução, alternância OPEN/HALF_OPEN, fila vazia, push antigo e push ausente. A sequência alternada é sintética; não representa uma captura do runtime.

Com `promtool` disponível, executar de `infrastructure/Configuration/observability`:

```sh
promtool test rules config/prometheus/tests/consignado-l3.test.yml
promtool test rules config/prometheus/tests/consignado-sre.test.yml
```

Validação executada com `promtool` 3.5.0, mesma versão padrão do Compose: cinco cenários novos / 15 verificações e 14 cenários existentes / 98 verificações passaram. `promtool check rules` aprovou as sete regras. O pacote oficial foi conferido contra o SHA-256 publicado no release. O cenário alternado manteve backlog e push fresco sem disparar; OPEN contínuo disparou em 3 min e resolveu em 4 min.

Não houve mudança na regra, no `for`, no redrive nem no receiver. Não foi enviado alerta sintético ou real nesta etapa. A prova de publicação SNS continua sendo a dos exercícios anteriores, descritos no [README de alertas](alertas/README.md).

## Próxima validação integrada

Seguir o [desenho L3 e seus gates](../../aidlc-docs/construction/sre-r1/l3-design.md). Com ambiente saudável e coorte isolada, a falha inicial terá teto de 15 s; interromper antes se qualquer mensagem atingir dois recebimentos ou houver DLQ inesperada. Não estender a falha para forçar firing.

Registrar as transições reais e o trabalho já em voo, reconciliar identidade/valor e conferir filas/DLQ. Depois, desenhar um detector de impacto sustentado com sinais efetivamente disponíveis, cobrindo frescor e ausência de chamadas durante OPEN. Somente sua prova integrada poderá fechar o critério de detecção e notificação da jornada.

O [plano AI-DLC](../../aidlc-docs/inception/sre-resilience/plans/execution-plan.md) mantém proteção, alerta, entrega e recuperação como evidências separadas.
