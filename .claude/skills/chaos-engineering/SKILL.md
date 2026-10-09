---
name: chaos-engineering
description: Skill especializada para planejamento, execução, automação e análise de experimentos de Engenharia do Caos em ambientes distribuídos e nuvem (AWS, GCP, Kubernetes).
---

# Agent Skill: Chaos Engineering Framework

Esta skill guia o agente no planejamento rigoroso, execução segura e extração de aprendizados a partir de experimentos de Engenharia do Caos.

## Fluxo de Trabalho (Workflow)

```
[1. Definir Steady State] ➔ [2. Formular Hipótese] ➔ [3. Configurar Blast Radius & Rollback] ➔ [4. Injetar Falha] ➔ [5. Analisar & Documentar]
```

---

### Etapa 1: Definição do Estado Estável (Steady State)
Defina a métrica de saída mensurável que indica o funcionamento normal do sistema sob condições normais de tráfego.

- **Métricas de Negócio**: Início de reprodução de vídeo/segundo, taxa de checkout bem-sucedido, logins por minuto.
- **Métricas de Engenharia (SRE)**: Latência (p95/p99), taxa de erros 5xx (< 0.1%), vazão (RPS).
- **Referências Recomendadas**: Consulte `chaos-engineering-reference.md` para exemplos de indicadores de estado estável.

---

### Etapa 2: Formulação da Hipótese
Crie uma afirmação clara e testável baseada nas jornadas críticas do usuário.

- *Estrutura*: "Mesmo ao injetar [Ação/Falha] no serviço [Target], o [Métrica de Steady State] permanecerá inalterado e não afetará a experiência do usuário."
- *Exemplo*: "Mesmo ao injetar 30ms de latência em 50% das requisições entre o serviço de autenticação e o cache, o tempo de resposta da API principal permanecerá dentro do SLO de 200ms."

---

### Etapa 3: Gestão do Blast Radius & Trava de Segurança (Stop Conditions)
Garanta que os danos em potencial sejam minimizados e contidos.

1. **Escopo do Experimento**:
   - Comece em ambiente de pré-produção ou staging.
   - Ao mover para produção, restrinja o impacto a um grupo canário ou subconjunto de alvos (ex: max 10% das instâncias).
2. **Configuração de Rollback Automático**:
   - Associe o experimento a alarmes de monitoramento (ex: alarme de latência > 500ms ou taxa de erro > 1%).
   - Se o alarme for disparado, acione imediatamente a ação de conversão/desfazimento (*rollback*).

---

### Etapa 4: Injeção de Falhas e Execução
Utilize frameworks e ferramentas apropriadas dependendo da infraestrutura:

| Cenário de Teste | Ferramenta / Mecanismo | Ação |
| :--- | :--- | :--- |
| **Queda de Instâncias / Pods** | Chaos Monkey / AWS FIS / Kubernetes | Encerrar pods/VMs aleatoriamente |
| **Injeção de Latência de Rede** | AWS FIS / ToxiProxy / Chaos Toolkit | Adicionar atrasos artificiais em chamadas HTTP/gRPC |
| **Estresse de Recursos (CPU/Memória)** | AWS FIS / Gremlin / Stress-ng | Elevar consumo de CPU/Memória até 95% |
| **Simulação de Failover Regional** | Chaos Kong / AWS FIS Cross-Region | Simular indisponibilidade de AZ ou Região inteira |

---

### Etapa 5: Análise de Resultados e Relatório SRE
Compare os dados do grupo de controle e do grupo experimental durante e após o teste:

- **Hipótese Confirmada**: O sistema suportou a falha sem degradação do *steady state*. O nível de confiança na arquitetura aumenta.
- **Hipótese Refutada**: Uma vulnerabilidade foi encontrada (ex: *retry storm*, esgotamento de *thread pools*, falta de *circuit breaker*).
- **Ações Pós-Experimento**:
  1. Registrar descobertas no relatório de *Post-Mortem*.
  2. Criar tarefas de engenharia para correção de fallbacks e configurações de timeout.
  3. Re-executar o experimento em pipeline CI/CD para validar a correção.

---

## Recursos e Referências Complementares
Para documentação detalhada sobre princípios teóricos, estudos de caso (Netflix, Google Cloud) e guias das ferramentas AWS FIS e Chaos Toolkit, consulte o arquivo de referência:
- `chaos-engineering-reference.md`
