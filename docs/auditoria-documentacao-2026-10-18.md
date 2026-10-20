# Auditoria documental — 18/10/2026

## Resumo executivo

Foram revisados 193 arquivos Markdown versionáveis do workspace, incluindo diretórios ocultos de regras e agentes e excluindo saídas de build, resultados locais e `.git`. A revisão aplicou a `tech-documentation-skill` e combinou verificações automáticas de links, duplicidade exata, codificação, hierarquia de títulos, endereços efêmeros e termos de estado com leitura semântica dos documentos centrais.

Os problemas que alteravam a interpretação do projeto foram corrigidos. O estado da PoC de SRE agora é consistente: encerrada em 18/10/2026, com R0 e L1 concluídos, L2 incompleto na conciliação final, L3 comprovado localmente e R2-R4 transferidos para backlog. A jornada local de quatro serviços no Jaeger aparece separada da validação ainda pendente no runtime Lambda/AWS.

## Correções realizadas

| Área | Problema | Correção |
|---|---|---|
| Links internos | Duas referências AI-DLC apontavam para `common/depth-levels.md`, arquivo inexistente. | Referências direcionadas para a seção Adaptive Depth de `common/core-rules.md`. |
| Validador de links | O script ignorava documentos ocultos e interpretava links aparentes dentro de blocos de código. | Inclusão de arquivos ocultos e exclusão de blocos cercados por crases ou tils. |
| Estado AI-DLC/SRE | Índices declaravam a PoC encerrada, enquanto o plano ainda indicava Construction ativa, ambiente ligado e L2 como próximo passo. | README, estado e plano alinhados ao fechamento; resultados incompletos permanecem explícitos e R2-R4 viraram backlog. |
| Tracing | Índices antigos diziam que a jornada completa ainda não havia sido vista no Jaeger. | Estado atualizado para jornada local de quatro serviços comprovada, mantendo a pendência do runtime AWS e de caminhos não exercitados. |
| Estado operacional | Um trecho afirmava que aplicações locais permaneciam em execução. | Texto passou a registrar que elas ficaram ativas naquela rodada e foram encerradas depois com Docker e EC2. |
| IPs efêmeros | Relatos L1/L2 continham link direto para um IP antigo da EC2. | Preservados os trace IDs e adotado o formato `http://<IP_ATUAL>:16686/trace/<id>`. |
| Conteúdo duplicado | O artigo “definitivo” repetia integralmente a v2; dois documentos do Guardião repetiam as fontes operacionais em `.claude/agents`. | Caminhos antigos foram convertidos em referências curtas para as fontes canônicas. |
| Hierarquia Markdown | O brainstorming de métricas saltava diretamente de H1 para H3. | Seções principais promovidas para H2. |
| Auditoria anterior | A auditoria de 16/10 podia ser confundida com o estado atual. | Documento marcado como histórico e este relatório definido como revisão vigente. |

## Fontes canônicas após a revisão

- Visão e navegação: [README principal](../README.md) e [índice de documentação](README.md).
- Estado verificável: [estado atual](estado-atual.md) e [fechamento da PoC SRE](sre/fechamento-poc-sre-observabilidade.md).
- Operação SRE: [guia SRE](sre/README.md), [alertas](../infrastructure/Configuration/observability/ALERTAS.md) e [tracing local](manuais/tracing-local.md).
- Execução AI-DLC: [plano de resiliência](../aidlc-docs/inception/sre-resilience/plans/execution-plan.md).
- Artigo final: [O seu SRE mostra o quê? — v2](sre/artigo-o-seu-sre-mostra-o-que-v2.md).

## Limites e obstáculos remanescentes

- Documentos históricos do AI-DLC preservam decisões, estados e endereços registrados na época. Eles não devem ser usados como inventário operacional atual.
- SDDs e ADRs descrevem propostas e estágios diferentes. O índice esclarece essa separação, mas a escolha formal de uma arquitetura target continua sendo uma decisão futura.
- Números de mercado e afirmações regulatórias nos brainstormings e narrativas não foram validados nesta auditoria. Eles exigem fontes primárias e data de consulta antes de publicação externa.
- A auditoria não prova disponibilidade dos links externos nem o estado dos serviços, pois o ambiente permaneceu desligado.
- Há documentação interna de ferramentas e skills no mesmo repositório. Ela foi incluída nas verificações estruturais, mas não foi reescrita quando não alterava a compreensão do produto.

## Como repetir a validação

Na raiz do repositório:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File applications/scripts/check-doc-links.ps1
git diff --check
```

O primeiro comando valida destinos locais de links Markdown fora de blocos de código. O segundo identifica erros de whitespace nas alterações versionadas.
