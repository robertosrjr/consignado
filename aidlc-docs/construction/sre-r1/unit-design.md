# R1/L1 — ensaio de falha transitória

Data: 18/10/2026. Continuidade autorizada: “pode ligar o ambiente e vamos testar”, seguida de “vamos testar”. A restrição de não ligar EC2 era exclusiva de 17/10; hoje há autorização explícita de início.

Escopo desta rodada: L1, cinco propostas novas de R$ 1.000,00 e cinco replays no JMeter. L2, L3 e alteração de alertas permanecem pendentes. Aplicação/emuladores locais; stack de métricas/traces na EC2 existente, sem nova infraestrutura ou permissões IAM.

## Desenho do ensaio

O helper `applications/scripts/test-legacy-transient.ps1` prepara uma cópia isolada do JMX e seus scripts em diretório de evidências novo, preservando o plano original. Insere um preprocessador somente na primeira proposta nova: POST autenticado de falha determinística de dois segundos ao controle R0. Essa posição evita consumir o prazo durante setup, compilação inicial e emissão de tokens do JMeter. A ativação é tentada uma única vez, sem renovar o prazo por falha do cliente.

Antes do ensaio: checar saúde dos três serviços, estado neutro do controle, captura recente, sessão de processos e filas locais vazias (disponíveis, invisíveis e atrasadas). O aquecimento é separado, usando uma proposta nova e um replay; não pertence à coorte L1. Conferir exportação remota e baseline de parâmetros antes da falha.

O helper lê filas apenas no ElasticMQ de loopback, com `--no-sign-request`; não consulta a SQS de alertas na AWS. Métricas locais do mock são comparadas antes/depois para identificar 503 simulados, efeitos únicos e replays no legado. A identidade final vem do JTL e do snapshot do worker. O controle de coleta contém hashes e resumo independente do próprio plano JMeter.

## Aceite, falhas e restauração

PASS exige dez HTTP 202, cinco identidades novas, cinco replays aceitos, cinco confirmações observadas na mesma sessão, ao menos uma falha simulada real, cinco efeitos únicos adicionais, nenhum replay adicional no ledger, filas/DLQ vazias e expiração observada antes do restore explícito. O trace deve identificar quais propostas sofreram retry; não afirmar que todas falharam se a evidência mostrar apenas parte da coorte.

Ausência de fontes/erro do harness produz INCOMPLETE. Resultado observado que viola critérios produz FAIL. Não ajustar TTL ou configuração do circuito para obter sucesso. Guardrail de tentativas: a falha de dois segundos termina antes do retry base de cinco segundos da configuração atual; confirmar esse parâmetro e redrive três antes da execução. Não renovar falhas ou modificar política de retry/redrive durante a rodada.

Restore em `finally` é idempotente; a expiração no mock independe da continuidade do script. Não reiniciar processos, purgar filas nem trocar chaves. Uma chamada em andamento pode terminar depois da expiração, mas L1 injeta apenas erro, sem latência. Recusar execução com coorte não isolada.

## Evidência e limites

Arquivos locais ignorados: JMX derivado, JTL, logs JMeter, ativação sem token, resultado, controle de captura e relatório conciliado. Documentar resultados reais e referências sanitizadas após executar. Verificar traces no Jaeger e frescor no Prometheus separadamente; nenhum teste curto promete disparar o alerta de legado com `for` de dois minutos. Não consultar a fila SQS cloud nem afirmar consumo de notificações.
