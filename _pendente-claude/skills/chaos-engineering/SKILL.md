---
name: chaos-engineering
description: "Use para planejar, revisar, executar com aprovação e relatar experimentos de Engenharia do Caos em sistemas distribuídos (local, CI, staging ou produção; AWS, GCP, Kubernetes ou processos simples). Cobre estado estável, hipótese, pré-requisitos, blast radius, condições de parada, catálogo de falhas e relatório. Não use para implementar retry, timeout ou circuit breaker (resilience-checker-skill) nem para teste de carga puro sem falha injetada."
---

# Chaos Engineering

Skill genérica, reutilizável em qualquer projeto. Ela define **o método**. Os cenários específicos de cada sistema ficam numa skill do projeto, que aponta para esta (veja "Extensão por projeto").

Detalhes, exemplos e modelos estão em `references/chaos-engineering-reference.md`.

## Fluxo

```
0. Reconhecer o ambiente → 1. Estado estável → 2. Hipótese → 3. Pré-requisitos
→ 4. Desenho e aprovação → 5. Linha de base → 6. Injeção → 7. Verificação e reset → 8. Relatório
```

### 0. Reconhecer o ambiente

Antes de propor qualquer coisa, descubra o que **existe** no projeto:
- ambientes (local, CI, staging, produção);
- orquestração (processos, containers, Kubernetes);
- nuvem e ferramentas de injeção já disponíveis;
- observabilidade (métricas, traces, logs, alertas);
- SLOs e error budget, se houver.

Proponha só ferramentas que existem ou que entram com uma mudança pequena e declarada. Use a escada de maturidade da referência para saber onde o projeto está.

### 1. Estado estável

Defina o comportamento normal em termos **mensuráveis da saída do sistema**:
- **métricas de saída:** vazão, taxa de erro, percentis de latência (p95/p99) ou uma métrica de negócio de alto nível (pedidos por segundo, logins por minuto);
- **asserções de correção do domínio**, obrigatórias em sistemas transacionais (pagamentos, crédito, estoque, mensageria): por exemplo, zero efeito duplicado, zero perda, saldos conciliados, ordem preservada por chave. Elas são verificadas durante o experimento e **ao final**, consultando o estado (banco, filas, sistema de destino).

Uma métrica de disponibilidade saudável não prova correção: um sistema pode responder 200 e duplicar efeitos.

### 2. Hipótese

Use este modelo:

> Ao injetar **[falha]** em **[alvo]** com **[intensidade e duração]**:
> - **durante:** [métricas de saída] ficam dentro de **[faixa de tolerância]** e **[asserções de correção]** se mantêm;
> - **depois:** o sistema volta ao estado estável em até **[tempo máximo de recuperação]**.

"Inalterado" raramente é a hipótese certa. Sistemas com degradação planejada (fila que cresce, fallback, modo somente leitura) **devem** variar dentro do previsto. O que não pode é sair da faixa, violar uma asserção de correção ou não se recuperar a tempo.

### 3. Pré-requisitos: não autorize sem todos

| Pré-requisito | Verificação |
|---|---|
| Observabilidade do alvo | métricas, traces e logs suficientes para isolar a causa de um desvio; sem isso, o experimento não ensina nada |
| Dependências mapeadas | upstream e downstream do alvo, para estimar o impacto indireto em cascata |
| Blast radius definido | alvos, porcentagem de tráfego ou instâncias, duração e janela de execução |
| Condição de parada **testada** | alarme ou limite que interrompe o experimento automaticamente, disparado de propósito ao menos uma vez antes |
| Rollback definido | como desfazer a falha e restaurar o estado |
| Error budget disponível | em produção: se o orçamento do período já foi consumido, o experimento fica bloqueado |
| Dados e efeitos seguros | em sistemas que movem dinheiro ou dados pessoais: contas e dados sintéticos e efeitos reversíveis. Nunca use dados pessoais reais |
| Pessoas avisadas | plantão e donos dos serviços sabem a janela, salvo em GameDay surpresa combinado com a liderança |

### 4. Desenho e aprovação

- Descreva o experimento **como código** (template do Chaos Toolkit, do AWS FIS, script versionado) com hipótese, alvos, ações, condições de parada e rollback.
- **Um humano aprova o desenho e os limites.** Depois de aprovado, o experimento pode rodar de forma automática e repetida (CI, agenda), desde que dentro dos limites aprovados e com parada automática.
- Qualquer mudança de alvo, intensidade, ambiente ou blast radius exige nova aprovação.

### 5. Linha de base

Rode o mesmo cenário **sem falha** e registre as métricas e as asserções. Sem grupo de controle simultâneo, a linha de base é a comparação.

### 6. Injeção

- Comece pela menor intensidade e pelo menor blast radius e aumente aos poucos.
- Acompanhe em tempo real. Se a condição de parada disparar, o experimento terminou, e isso já é um resultado.
- Use o catálogo de falhas da referência. Cubra **infraestrutura** (instância, rede, recurso) e **semântica distribuída** (entrega duplicada, timeout depois do commit, processo morto no meio da operação, mensagem fora de ordem, relógio desalinhado).

### 7. Verificação e reset

- Verifique as asserções de correção **depois** que o sistema estabiliza, não só durante.
- Meça o tempo real de recuperação.
- **Restaure o estado** (dados, filas, caches, configuração) antes do próximo experimento. Uma execução não pode contaminar a seguinte.
- **Teste negativo, só fora de produção:** desligue a proteção que está sendo validada e rode de novo. Se o defeito não aparecer, o experimento não é sensível o bastante para provar que a proteção funciona.

### 8. Relatório de experimento

Registre num **relatório de experimento** (não é post-mortem, que é para incidente não planejado), usando o modelo da referência:
- hipótese confirmada ou refutada, com os dados;
- comparação com a linha de base;
- **pessoas e processos:** o alerta chegou ao plantão? O dashboard mostrou o problema? O runbook funcionou?
- ações de correção com dono;
- quando rodar de novo (de preferência, automatizado).

Hipótese refutada é um bom resultado: a fraqueza foi encontrada no experimento, e não num incidente.

## Regras

- Sem hipótese, estado estável, condição de parada testada e aprovação humana do desenho, não há experimento.
- Não execute em produção antes de o mesmo experimento passar nos degraus anteriores da escada de maturidade.
- Produção é o objetivo da disciplina, não o ponto de partida. Em setores regulados, siga a gestão de mudanças da organização.
- Nunca amplie o blast radius durante a execução.
- Nunca use dados pessoais reais nem gere efeito financeiro irreversível.
- Não invente ferramentas que o projeto não tem. Se faltar algo, proponha como pré-requisito separado.

## Extensão por projeto

Cada projeto pode ter uma skill própria com os seus cenários, as suas asserções de correção, a sua linha de base e onde publicar a evidência. Essa skill aponta para esta como método e não repete o fluxo.

## Delegação

- Implementar retry, timeout, circuit breaker, bulkhead e fallback: `resilience-checker-skill`.
- Métricas, painéis, SLOs e alertas: `sre-observability-skill` e `spring-metrics-skill`.
- Dados pessoais em telemetria e em massa de teste: `lgpd-sre-compliance-skill`.
- Agentes com permissão de executar ações: `saif-skill` (humano no loop).
