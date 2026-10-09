# Referência: Chaos Engineering

## 1. Definição

Engenharia do Caos é a disciplina de fazer **experimentos controlados** num sistema para ganhar confiança na sua capacidade de suportar condições adversas. Em sistemas distribuídos, a interação entre componentes produz comportamentos que nenhum teste isolado mostra:
- tempestades de retentativa;
- falhas em cascata;
- fallbacks mal configurados;
- timeouts incoerentes entre camadas;
- pontos únicos de falha;
- efeitos duplicados ou perdidos.

## 2. Princípios

Segundo os *Principles of Chaos Engineering* (principlesofchaos.org):

**Etapas de um experimento:**
1. Definir o estado estável como uma saída mensurável do sistema.
2. Supor que o estado estável se mantém no grupo de controle e no grupo experimental.
3. Introduzir variáveis que refletem eventos do mundo real.
4. Tentar refutar a hipótese, procurando diferença entre os grupos.

**Princípios avançados:**
- hipóteses sobre o estado estável, medido na saída do sistema e não em detalhes internos;
- variar eventos do mundo real, priorizados por impacto e frequência (inclusive eventos que não são falha, como autoscaling e picos);
- rodar em produção (objetivo final da disciplina; veja a escada de maturidade);
- automatizar para execução contínua;
- minimizar o blast radius.

**Como esta skill interpreta os princípios:**
- "Estado estável" tolera variação dentro de uma faixa definida. A hipótese explicita a faixa e o tempo de recuperação.
- Em sistemas transacionais, a saída do sistema inclui **correção**: asserções de domínio verificadas no estado final.

## 3. Escada de maturidade

| Nível | Ambiente | Exemplos de mecanismo | Pré-requisito para subir |
|---|---|---|---|
| 1 | Local | stubs e mocks com modo de falha, matar processos, parar dependências, proxies de falha (Toxiproxy) | hipóteses escritas e asserções de correção automatizadas |
| 2 | CI | os mesmos experimentos em pipeline, com containers ou cluster efêmero (kind, k3d); Chaos Toolkit | experimentos reprodutíveis, com reset e linha de base automáticos |
| 3 | Staging | Chaos Toolkit, AWS FIS, falhas de rede, recurso e instância | observabilidade equivalente à de produção e condição de parada testada |
| 4 | Produção canário | AWS FIS, Chaos Monkey ou similar, com alvo restrito (pequena porcentagem de instâncias ou tráfego) | error budget disponível, gestão de mudança, plantão avisado |
| 5 | Produção contínua | experimentos pré-aprovados agendados, com parada automática | histórico de execuções no nível 4 sem incidente causado pelo experimento |

O agente identifica o nível atual do projeto e propõe experimentos **desse** nível. Subir de nível é uma decisão humana.

## 4. Catálogo de falhas

### 4.1 Infraestrutura

| Falha | O que revela | Mecanismos |
|---|---|---|
| Queda de instância, pod ou processo | redundância, failover, reconexão | kill de processo, Chaos Monkey, AWS FIS, falha de pod |
| Latência de rede | timeouts, circuit breaker, filas de threads | Toxiproxy, AWS FIS, `tc netem` |
| Perda de pacote e conexão recusada | retentativa, idempotência do cliente | Toxiproxy, AWS FIS |
| Estresse de CPU, memória ou disco | autoscaling, degradação, limites | stress-ng, AWS FIS |
| Indisponibilidade de dependência gerenciada | fallback, modo degradado | parar o emulador, bloquear o endpoint, AWS FIS |
| Perda de zona ou região | failover geográfico | AWS FIS (cross-AZ), exercícios de região |

### 4.2 Semântica distribuída

Estas falhas quebram **correção** sem quebrar disponibilidade. Em geral exigem que o alvo ou um stub do destino colabore, além da rede.

| Falha | O que revela | Como injetar |
|---|---|---|
| Entrega duplicada de mensagem | idempotência do consumidor | reenviar a mesma mensagem; reduzir o visibility timeout abaixo do tempo de processamento |
| Timeout **depois** do commit no destino | reenvio que duplica efeito | stub do destino que grava e não responde |
| Processo morto no meio da operação | estados intermediários, retomada, locks órfãos | matar o processo entre a chamada externa e a gravação do resultado |
| Mensagem fora de ordem | dependência de ordem não garantida | reordenar a entrega; consumidores concorrentes |
| Dois consumidores na mesma mensagem | exclusão mútua, fencing | várias réplicas e visibility timeout curto |
| Relógio desalinhado | expiração, janelas, TTL | deslocar o relógio do processo ou injetar um clock |
| Resposta parcial ou corrompida | validação, desserialização, DLQ | stub do destino com payload inválido |
| Retentativa em massa do cliente | tempestade de retentativa, cotas | gerador de carga repetindo em erro |

## 5. Checklist antes de autorizar

- [ ] Hipótese no modelo do SKILL (faixa de tolerância e tempo de recuperação)
- [ ] Métricas de saída e asserções de correção definidas e automatizadas
- [ ] Observabilidade do alvo suficiente para isolar a causa
- [ ] Dependências mapeadas e impacto indireto estimado
- [ ] Blast radius: alvos, porcentagem, duração e janela
- [ ] Condição de parada definida **e testada**
- [ ] Rollback e reset de estado definidos
- [ ] Linha de base registrada
- [ ] Dados sintéticos e efeitos reversíveis
- [ ] Error budget disponível (produção)
- [ ] Gestão de mudança e plantão avisados (staging compartilhado e produção)
- [ ] Desenho aprovado por um humano, com nome e data

## 6. Experimento como código (exemplo com Chaos Toolkit)

```json
{
  "title": "Destino grava e não responde: o consumidor não duplica o efeito",
  "description": "Hipótese: com 5% de timeout depois do commit, nenhum efeito duplicado e recuperação em até 2 min.",
  "steady-state-hypothesis": {
    "title": "Sem duplicidade e erro abaixo de 1%",
    "probes": [
      { "type": "probe", "name": "duplicatas-zero", "tolerance": 0,
        "provider": { "type": "http", "url": "http://destino-stub/metrics/duplicates" } },
      { "type": "probe", "name": "erro-abaixo-de-1pct", "tolerance": { "type": "range", "range": [0, 0.01] },
        "provider": { "type": "http", "url": "http://observabilidade/api/error-rate" } }
    ]
  },
  "method": [
    { "type": "action", "name": "ativar-commit-then-timeout",
      "provider": { "type": "http", "url": "http://destino-stub/chaos", "method": "POST",
                    "arguments": { "mode": "commit-then-timeout", "rate": 0.05 } },
      "pauses": { "after": 300 } }
  ],
  "rollbacks": [
    { "type": "action", "name": "desativar-caos",
      "provider": { "type": "http", "url": "http://destino-stub/chaos", "method": "DELETE" } }
  ]
}
```

No AWS FIS, o equivalente é um *experiment template* com ações, alvos por tag e *stop conditions* ligadas a alarmes do CloudWatch.

## 7. Modelo de relatório de experimento

```markdown
# Experimento: <título>  (AAAA-MM-DD, nível de maturidade N)

## Hipótese
<no modelo do SKILL>

## Desenho
Alvos, falha, intensidade, duração, blast radius, condição de parada, rollback.
Aprovado por: <nome>, em <data>. Experimento como código: <caminho>.

## Ambiente
Versões, commit, instâncias, origem da carga.

## Linha de base × experimento
| Métrica ou asserção | Linha de base | Durante | Depois | Faixa | OK? |

## Recuperação
Tempo real até o estado estável: <x> (limite: <y>).

## Teste negativo (fora de produção)
Proteção desligada: <qual>. O defeito apareceu? <sim/não, com dados>.

## Pessoas e processos
Alertas disparados (quais, para quem, em quanto tempo), dashboards, runbook.

## Resultado
Hipótese CONFIRMADA | REFUTADA. O que aprendemos.

## Ações
| Ação | Dono | Prazo |

## Próxima execução
Manual | agendada | no pipeline <nome>.
```

## 8. GameDay

O GameDay é um exercício combinado em que a equipe enfrenta falhas injetadas como se fossem reais. Ele testa **pessoas e processos**, e não só o código: detecção, escalonamento, comunicação, runbooks e decisão sob pressão. O programa DiRT do Google é a referência mais conhecida. Use o mesmo método (hipótese, limites, parada, relatório) e acrescente observadores que registram a linha do tempo da resposta.

## 9. Integração com SRE

- **SLIs e SLOs** dão o estado estável e as faixas de tolerância.
- **Error budget** dá a margem para experimentar em produção e bloqueia experimentos quando já foi consumido.
- **Postmortem** continua sendo o documento de incidente não planejado. Um experimento que causa impacto **fora** dos limites aprovados vira incidente e ganha postmortem.
- **Toil:** automatizar experimentos de failover e recuperação reduz trabalho manual repetitivo.

## 10. Leituras

- Principles of Chaos Engineering: principlesofchaos.org
- Beyer, B. et al. *Site Reliability Engineering*. O'Reilly, 2016, capítulos sobre SLOs, error budget, integridade de dados e postmortem.
- Rosenthal, C.; Jones, N. *Chaos Engineering: System Resiliency in Practice*. O'Reilly, 2020.
- AWS Fault Injection Service: documentação de experiment templates e stop conditions.
- Chaos Toolkit: documentação de experimentos, probes e rollbacks.
