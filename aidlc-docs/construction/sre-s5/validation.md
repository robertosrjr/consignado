# S5 — Durabilidade: validação

Data: 23/10/2026. Execução inteiramente local, sem Docker ou AWS.

## Resultado

Durabilidade do worker e reconciliação de órfãos validadas no escopo local. Estado e histórico transacionais em DynamoDB; Sweeper de RECEIVED com índice esparso; decisão da Inbox reparada na reentrega de estado terminal, com falha propagada para impedir ACK prematuro.

## Evidências

- Build completo `mvn -o "-Dmaven.repo.local=C:\Users\rober\.m2\repository" verify -Pintegration`: BUILD SUCCESS, 10 módulos, 473 testes, zero falhas/erros/ignorados. Gates de formato, cobertura, Enforcer e ArchUnit aprovados. Log: `logs/s5-final.log`.
- Worker: 147 testes e 6 ITs; `DynamoDbProposalRepositoryIT` (2) exercita commit real com resposta perdida, reconstrução do repositório, replay e terminal sem regressão. `ProcessProposalServiceTest` (12) inclui recuperação da decisão após falha, sem nova chamada ao legado.
- `OrphanSweeperTest`: falha de publicação, preservação da pendência e retry. `OrphanSweeperIT`: DynamoDB Local + ElasticMQ reais, sem consumidor de Streams; órfão publicado e removido do índice.
- Queda real: `node scripts/durability-local.cjs`, na mesma sessão de `local-up.ps1`. Três propostas confirmadas, worker encerrado à força, três mensagens terminais reenviadas à fila e três propostas novas aceitas enquanto o worker estava parado. Novo processo confirma as seis; delta do legado = seis averbações únicas. PASS em `applications/.local/durability-worker-restart.json` (23:10:34 UTC). Nenhuma chave ou segredo no relatório.

## Limites

- DynamoDB Local permanece em memória: reiniciar a infraestrutura apaga os dados; a garantia exercitada é reinício do worker mantendo banco e fila ativos.
- Histórico transacional funciona como trilha durável; não foi introduzido publicador externo de eventos de negócio. Métricas de negócio não têm entrega exatamente uma vez.
- Contadores de estado são locais ao processo, não inventário durável. A conciliação de capacidade consulta STATE.
- Timeout do legado depois de averbar e antes da resposta depende da idempotência do próprio legado por chave; não foi injetado no ensaio de processo. O timeout pós-commit de armazenamento foi injetado no IT.
- A primeira carga S6 encontrou um 503 de timeout do emulador e divergência entre respostas 202 e averbações; resultado preservado como FAIL, sem alegar zero divergência HTTP no envelope inicial.
