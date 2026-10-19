# R0 — desenho da unidade de preparação

Data: 17/10/2026. Pedido de continuidade: “seguimos o plano do ia-dlc então”. O usuário autorizou seguir o plano; a implementação começa localmente pelo R0, sem iniciar EC2 ou publicar alterações.

## Componentes e unidade

Uma unidade incremental no módulo `mock-legacy`, com apoio dos scripts locais. Não há novo serviço ou armazenamento. Dependências: `LegacyProperties` fornece a baseline, o relógio monotônico existente define expiração e `AverbacaoController` consulta a política efetiva antes de simular falha. Ledger, capacidade e contrato de negócio permanecem os mesmos.

`LegacyFaultPolicy` conserva uma única sobreposição temporária, com acesso sincronizado. A política efetiva é calculada em cada leitura: depois do TTL retorna à baseline sem depender de scheduler ou script externo. Uma chamada já iniciada conserva a política capturada; restaurar impede novas falhas, sem cancelar um efeito tardio em andamento.

`FaultControlController` existe somente com perfil `sre-experiment` e propriedade `legacy.fault-control.enabled=true`. Exige token próprio com ao menos 32 caracteres e origem loopback, sem confiar em headers de encaminhamento. A baseline estática deve ser neutra; caso contrário a ativação do controle falha no startup para não chamar retorno a uma falha permanente de restauração.

## Contrato e regras

- `GET /internal/sre/fault`: estado atual sem segredos.
- `POST /internal/sre/fault`: JSON `latencyMs`, `errorRate`, `durationMs`; latência de 0 a 10.000 ms, erro de 0 a 1 finito, duração de 1 a 300.000 ms, ao menos um efeito. Uma falha já ativa causa 409; não há renovação silenciosa.
- `DELETE /internal/sre/fault`: restauração idempotente. Todos os métodos exigem `X-SRE-Control-Token` e resposta sem cache.
- Fora do perfil ou com flag desligada a rota não existe. Token incorreto causa 401; origem não loopback causa 403; payload inválido causa 400. Corpo de erro não deve ecoar valores sensíveis.
- `local-up.ps1 -SreFaultControl` habilita exclusivamente o mock, usando token recebido do ambiente, sem persistir ou imprimir. O cliente local controla a falha e grava manifest sanitizado; nunca inclui token ou payload de negócio.

## Requisitos não funcionais e validação

RF-01/02/09 e RNF-01/02/05/06 do incremento. Tests com relógio controlado verificam expiração no limite, baseline, conflito, limites e restore repetido. Integração MVC verifica isolamento do endpoint, token/origem, erro determinístico, restauração e replay do mesmo ledger. Um teste de contexto verifica que habilitação incompleta permanece fechada. Formatação, cobertura e testes do reator afetado devem ser verificados; nenhum teste implica validação remota.

R0 inclui um manifest de controle e preflight local mínimo; coleta integrada de toda a coorte, métricas frescas e trace remoto continua sendo gate dos experimentos R1. Nenhum novo manifest deve ser apresentado como relatório completo de SLI.

## Infraestrutura, rollback e limites

Sem mudança de infraestrutura ou IAM. Retirar a flag/perfil numa futura inicialização elimina o endpoint. Durante uma execução, DELETE ou expiração elimina a sobreposição sem reiniciar a JVM. Não purgar filas nem apagar ledger. Proxy local e comprometimento do host não são contidos por loopback; não encaminhar/publicar a rota e manter o token separado das credenciais do legado.

## Etapas da unidade

- [x] Analisar baseline e desenhar componentes, contratos, regras e controles.
- [x] Implementar política, controle HTTP e apoio local.
- [x] Validar isolamento, expiração, identidade e checks do reator afetado.
- [x] Registrar [resultados locais](validation.md) e atualizar estado AI-DLC.

Este desenho reúne Functional Design, NFR Design e Infrastructure Design proporcionais à unidade brownfield. Unidades seguintes continuam no plano e só serão detalhadas quando iniciadas.
