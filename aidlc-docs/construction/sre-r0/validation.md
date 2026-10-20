# R0 — resultado da validação local

Data: 17/10/2026 (America/Sao_Paulo). Estado: **implementação local validada; exercício integrado R1 não iniciado**.

## Resultado

Controle de falhas implementado no mock, desligado por padrão, com perfil e flag explícitos, token próprio, restrição loopback, limites, expiração monotônica e restauração idempotente. O iniciador recebeu `-SreFaultControl`; o cliente gera manifest sanitizado e tem modo `-PrepareOnly` sem rede. Ver [desenho](unit-design.md) e [guia operacional](../../../docs/sre/preparacao-falhas-legado.md).

Comando executado a partir de `applications/`, usando o cache Maven do usuário disponível no ambiente:

```powershell
mvn -B -ntp '-Dmaven.repo.local=C:/Users/rober/.m2/repository' -pl mock-legacy -am verify
```

| Módulo | Testes | Falhas / erros |
|---|---:|---:|
| shared | 67 | 0 / 0 |
| platform-telemetry | 5 | 0 / 0 |
| platform-starter | 37 | 0 / 0 |
| mock-legacy | 30 | 0 / 0 |
| **Total** | **139** | **0 / 0** |

Reator de cinco entradas, incluindo o parent: BUILD SUCCESS. Enforcer, Spotless e gates JaCoCo dos módulos passaram. O JAR do mock foi empacotado. Não é um `verify` completo dos nove módulos da aplicação.

As verificações novas cobrem: baseline estática preservada; limites de entrada e valores não finitos; expiração no limite sem chamada de restore/scheduler; conflito de ativação; restauração repetida; rota ausente por padrão; perfil e flag necessários; inicialização recusada sem token adequado ou com baseline não neutra; token inválido; acesso remoto com header de encaminhamento; erro determinístico; preservação do resultado idempotente no ledger após expiração/restauração.

Os testes de política usam relógio controlado e os testes MVC executam o mock dentro do contexto de teste. Demonstram o mecanismo, sem medir temporização real da jornada de produção. Não comprovam timeout no worker, detecção pelo Prometheus, envio de alerta ou entrega de trace ao Jaeger; esses são gates do R1.

## Scripts e documentação

- AST PowerShell dos dois scripts alterados/criados: válido.
- `sre-legacy-fault.ps1 -Action Activate -DurationMs 2000 -PrepareOnly`: gerou manifest `PREPARED`, sem rede ou token.
- Validador de links: 115 documentos, nenhum destino local ausente no momento da execução.
- `git diff --check` com tratamento CRLF do projeto: sem erros de whitespace nos arquivos acompanhados; arquivos novos conferidos separadamente.

Antes da validação bem-sucedida, o Maven padrão tentou um cache sem permissão e uma tentativa de formatação usou glob onde o plugin esperava expressão regular. Foram corrigidos selecionando o cache existente e formatando apenas o módulo mock; não houve alteração de dependências nem redução dos checks para contornar falhas.

## Estado operacional e próximo gate

Nenhuma aplicação da PoC foi iniciada; os únicos contextos de aplicação foram os dos testes. Não houve acesso à EC2, Docker remoto, Grafana ou SNS. Permanece a restrição explícita do usuário: não ligar a EC2 em 17/10/2026.

Próximo pacote: R1/L1, ensaio com cinco propostas e 503 temporário, após preflight de coorte, tentativas, filas, estado e coleta. O manifest de controle não substitui conciliação nem prova de recuperação de negócio. R2/R3/R4 seguem pendentes.
