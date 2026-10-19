# R1/L1 — resultado de execução e validação

Data: 18/10/2026. Escopo desta entrega: L1 concluído; R1 permanece parcial, com L2/L3 e avaliação do detector pendentes.

## Evidência funcional

O [relato do exercício](../../../docs/sre/exercicio-falha-legado-l1.md) reúne cronologia, massa e limites. Tentativa válida: `applications/.local/legacy-l1-20261018-03/`. JMeter: dez POSTs, todos 202, cinco novos e cinco replays. Worker: cinco identidades `CONFIRMED`. Mock: deltas de uma falha simulada, cinco efeitos únicos e zero replay adicional no ledger. Filas locais e DLQ vazias; controle neutro antes da restauração explícita.

Jaeger: cinco jornadas com os quatro serviços, uma com 503 seguido de 200. O trace `055f4ce43f63e70323abc640456e4a0d` tem quatorze spans e nenhuma referência interna sem resolução. As outras quatro jornadas têm onze spans cada, sem retry. A amostra não comprova que toda proposta enfrentou falha nem que o circuito abriu.

## Correções encontradas ao executar

- Harness: argumentos nativos passaram a usar array de strings; JMX derivado é salvo sem adicionar whitespace em campos `filename` vazios. As tentativas `01`/`02` foram preservadas como INCOMPLETE, sem propostas ou falha ativa.
- Snapshot no Windows: leitura com FileShare ReadWrite/Delete e retry curto/limitado para I/O. AST válido; cinquenta leituras com o produtor ativo retornaram a sessão esperada.
- Coletor: inicialização do cliente DynamoDB movida para antes da leitura do corte. A primeira captura recusou corretamente um snapshot com mais de cinco segundos. A captura posterior confirmou cobertura completa sem relaxar a regra de frescor.

## Checks executados

Empacotamento de todos os serviços necessários: `mvn -pl worker,local-env,ingestion-api,mock-legacy -am package -DskipTests`, BUILD SUCCESS. Esse empacotamento não executou testes e não é homologação completa.

Após a correção do coletor:

```powershell
mvn -B -ntp '-Dmaven.repo.local=C:/Users/rober/.m2/repository' -pl local-env -am test `
  '-Dtest=SreCaptureTest,SreCaptureMainTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

BUILD SUCCESS; sete testes focados, zero falhas/erros: cinco de normalização e dois da CLI. Checks de formatação/Enforcer executados pelo ciclo passaram. Não foi um `mvn verify` completo da aplicação nesta data. A execução real de nova captura após a correção é a verificação integrada do problema de inicialização.

## Conciliação e publicação

`capture-02` usou JTL/resumo originais com hashes válidos, Inbox consistente e snapshot atual da mesma sessão. Resultado: cobertura completa, nenhuma inconsistência, cinco aceitas/confirmadas, nenhuma aberta, R$ 5.000 recebidos e confirmados.

Relatório SRE **FAIL**, saída CLI 3: latência 9/10 abaixo de 100 e 200 ms, abaixo das metas 95%/99%. Aceite 10/10. Coorte standard ainda imatura para o prazo de 2.700 s; não antecipado. O PASS funcional do L1 não substitui esse resultado de SLI.

Snapshot publicado com o script existente no grupo estável `sre-experiment/env/local/instance/desktop-psq0nq3`. Prometheus confirmou `coverage_complete=1`, `report_status=1` e corte `1792331499`. Nenhum dashboard ou limiar de alerta alterado. Há R$ 1.000 de aquecimento fora da coorte L1 nos contadores cumulativos da sessão.

## Estado e limites operacionais

EC2 `i-082b5f183d18a8d25`, sa-east-1, iniciada mediante autorização do usuário. IP observado: `18.228.193.162`; não assumir estabilidade após futuro stop/start. Stack de observabilidade ativa; aplicações locais com captura e endpoints atualizados. Controle de falha restaurado; ambiente mantido ativo para inspeção.

Sem purga, reset, reinício durante a coorte, alteração de retry/redrive, consulta à SQS cloud ou disparo sintético de alertas. Não foi validada notificação SNS nesta rodada. A restrição de 17/10 foi preservada como histórica e não impede o início explicitamente solicitado em 18/10.

## R1/L3 — ensaio local em 18/10/2026

Continuidade solicitada pelo usuario depois do encerramento do ambiente. O estado operacional descrito na seção L1 acima é histórico: EC2 e serviços locais foram encerrados e não foram iniciados neste ensaio.

Teste novo LegacyCircuitRecoveryTest: HTTP real local, circuito real e fila em memória com redrive3. Amostra20, abertura, 100 passos bloqueados, preservação de recebimentos e recuperação das mesmas20 identidades sem DLQ. Versão inicial dentro de28 testes focados aprovados; versão final com transição automática de10s aprovada separadamente (1 teste, 15,35s, BUILD SUCCESS). Não substitui prova concorrente com SQS/Inbox, ledger financeiro, conciliação ou entrega SNS. Detalhes e limites em [desenho L3](l3-design.md) e [relato](../../../docs/sre/exercicio-circuit-breaker-l3.md).

Fixture promtool de cinco cenários preparada para verificar OPEN contínuo, alternância, fila vazia e push stale/ausente. Validação concluída: promtool3.5.0 aprovou os cinco cenários/15 verificações novos, os14 cenários/98 verificações existentes e as sete regras. Alternância OPEN/HALF_OPEN com backlog e push fresco não dispara, confirmando a lacuna. OPEN contínuo dispara em3min e resolve em4min. Nenhuma regra publicada ou alterada. Evidências preservadas em applications/.local/legacy-l3-20261018-local/. O gate integrado R1 permanece aberto; L2 ainda tem fechamento offline pendente.
