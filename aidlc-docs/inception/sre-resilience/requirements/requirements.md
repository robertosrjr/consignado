# Requisitos do incremento SRE

Data: 17/10/2026. Status: propostos, prontos para revisão. Referência: [baseline](../reverse-engineering/system-overview.md).

## Objetivo e escopo

Demonstrar, com evidências reproduzíveis, como a PoC detecta falha de uma dependência, protege o processamento e recupera propostas. Evoluir o exercício já realizado com o worker, começando pelo legado. A entrega atual é o plano; alterações de aplicação, regras, JMX e execução pertencem à Construction futura.

| ID | Requisito funcional | Aceite da futura implementação |
|---|---|---|
| RF-01 | Executar preflight e registrar baseline | Configuração efetiva, coorte, filas, frescor, serviços, relógios e rollback registrados; impedir início sem condições mínimas. |
| RF-02 | Injetar falha determinística e temporária no mock | Falha restrita ao teste, com prazo e restauração independente do sucesso do script; preservar processo, ledger e chaves. |
| RF-03 | Demonstrar recuperação após 503 transitório | Mesmas propostas e chaves são recuperadas, sem efeito duplicado ou DLQ inesperada; registrar tentativas e tempo de recuperação. |
| RF-04 | Demonstrar resposta ambígua após timeout | Verificar se houve commit tardio; replay mantém a identidade e recupera resultado sem nova entrada no ledger do mock. |
| RF-05 | Validar circuito e detecção de indisponibilidade | Observar CLOSED/OPEN/HALF_OPEN, backpressure e alerta aplicável; falha não detectada deve resultar em lacuna explícita e correção testada. |
| RF-06 | Identificar e recuperar uma proposta na DLQ | Mostrar profundidade com coleta válida, causa e redrive individual seguro; reconciliar mensagem, proposta e efeito final. |
| RF-07 | Medir carga progressiva e recuperação | Registrar carga oferecida/atingida, 429/5xx, latência, backlog e drenagem; interromper nos limites do protocolo. |
| RF-08 | Exercitar resposta a incidente | Operador usa alerta, dashboard e trace, registra diagnóstico, ação, recuperação e notificação resolved até SNS. |
| RF-09 | Produzir evidência conciliável | Relatório por execução com coorte, contagens/valores, cobertura, cronologia e estado PASS/FAIL/INCOMPLETE/ABORTED. |

| ID | Requisito não funcional | Critério |
|---|---|---|
| RNF-01 | Segurança e privacidade | Sem PII, tokens, chaves ou corpos sensíveis nos relatórios; sem identificadores de proposta/fingerprint como labels. |
| RNF-02 | Reversibilidade | Nenhuma purga geral, exclusão de volume ou reinício que apague o estado usado para provar recuperação; rollback com prazo e responsável. |
| RNF-03 | Integridade de medição | Ausência de dados não conta como sucesso; HTTP 202, confirmação, rejeição de negócio e falha técnica são populações distintas. |
| RNF-04 | Escopo de comprovação | Informar amostragem e cobertura; ledger do mock não comprova exactly-once financeiro em produção; sem SLA externo. |
| RNF-05 | Isolamento operacional | Nenhuma execução agora; futuras falhas apenas na PoC com ativação explícita; não acionar rotas de automação fora do exercício. |
| RNF-06 | Validação proporcional | Testes de comportamento de retry/identidade, regras e rollback antes do exercício integrado; evitar testes que só repitam implementação. |

## Hipóteses e interpretação

- Uma falha transitória recuperada antes de esgotar tentativas preserva todas as propostas da coorte e produz um efeito por identidade no mock.
- Um timeout não determina sozinho se houve efeito. A verificação do ledger e o replay idempotente resolvem essa ambiguidade no escopo da PoC.
- Falha prolongada pode produzir DLQ e exigir ação humana. DLQ não é rejeição de negócio nem confirmação.
- Um detector que não dispara nas condições esperadas reprova a hipótese de detecção; isso não será ocultado ajustando a duração do circuito para a apresentação.
- Crescimento de backlog durante carga não comprova perda. É necessário acompanhar a coorte até o corte final e declarar pendências.

PASS exige todos os critérios obrigatórios do cenário e cobertura completa da coorte. FAIL indica critério violado com evidência suficiente. INCOMPLETE indica evidência insuficiente ou pendência no corte. ABORTED indica interrupção pelo protocolo de segurança; registrar também o resultado parcial observado.

## Exclusões e decisões

Não incluir persistência durável do worker, novo serviço cloud, novo SLA, ativação de Business Fingerprint v2, novos dashboards permanentes ou reescrita do tracing. A consulta à SQS de alertas na AWS continua fora da validação atual; a evidência de notificação termina no SNS. Identificação técnica do trace na resposta é conveniência opcional, sem bloquear a prioridade do legado.

A capacidade nominal de 180 TPS e os limites experimentais do plano são hipóteses de laboratório, não SLOs aprovados. Preservar o [contrato de medição existente](../../../../docs/sre/contrato-medicao.md) e registrar qualquer proposta de mudança separadamente.
