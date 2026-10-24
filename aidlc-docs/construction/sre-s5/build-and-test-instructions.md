# S5 — Reprodução local

Em applications/, com Java 25 e Maven 3.9:

```powershell
mvn -o "-Dmaven.repo.local=C:\Users\rober\.m2\repository" verify -Pintegration
```

O perfil integration executa DynamoDB Local e ElasticMQ sem Docker. Manter gates de cobertura, Spotless, Enforcer e ArchUnit. Os testes de recuperação ficam em DynamoDbProposalRepositoryIT, ProcessProposalServiceTest, OrphanSweeperTest e OrphanSweeperIT; os demais testes protegem o fluxo preexistente.

Em outro ambiente, substituir o caminho de cache Maven; modo offline requer dependências já disponíveis. Os emuladores dos testes são encerrados pelos próprios testes.

Para capacidade após build verde:

```powershell
.\scripts\local-up.ps1 -PushgatewayAddress '' -OtlpTracesEndpoint ''
node scripts/capacity-local.cjs 15
.\scripts\local-down.ps1
```

Relatório em applications/.local/capacity-*/report.json. Não executar a carga contra ambiente externo. O ensaio interrompe a progressão em divergência e preserva evidência parcial.

Orquestração completa, com Prometheus direto, DEBUG desativado, timeouts locais explícitos e encerramento em finally:

```powershell
.\scripts\run-capacity-local.ps1 -Seconds 60 -Rates '1,5'
```

Adicionar `-TestWorkerRestart` para repetir a queda real. Não usar sobre sessão preexistente. A avaliação de latência do gerador é no cliente; o SLO oficial usa o histograma no servidor.
