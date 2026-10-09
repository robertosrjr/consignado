# Guia de Referência Técnica: Chaos Engineering & SRE

Este guia reúne os conceitos fundamentais, princípios operacionais e ferramentas oficiais para a prática de **Chaos Engineering (Engenharia do Caos)** integrada à cultura de **Site Reliability Engineering (SRE)**.

---

## 1. O que é Chaos Engineering?
A **Engenharia do Caos** é a disciplina de realizar experimentos controlados em um sistema com o objetivo de construir confiança na sua capacidade de suportar condições adversas em ambientes de produção [71, 79]. 

Em sistemas distribuídos modernos (baseados em microserviços e nuvem), o comportamento interativo entre componentes pode gerar resultados imprevisíveis [72, 80]. A engenharia do caos atua proativamente para identificar vulnerabilidades sistêmicas — como configurações inadequadas de *fallback*, tempestades de retentativas (*retry storms*), falhas em cascata e pontos únicos de falha (*Single Points of Failure*) — antes que elas afetem os usuários em produção [72, 80].

---

## 2. Princípios Oficiais da Engenharia do Caos
De acordo com o *Principles of Chaos Engineering*, a condução ideal de experimentos segue quatro etapas e cinco princípios avançados [73, 75, 81, 83]:

### Etapas do Experimento
1. **Definir o Estado Estável (Steady State)**: Estabelecer uma medida mensurável que indique o comportamento normal do sistema [74, 82].
2. **Formular a Hipótese**: Supor que este estado estável permanecerá inalterado tanto no grupo de controle quanto no grupo experimental [74, 82].
3. **Introduzir Variáveis do Mundo Real**: Simular eventos como queda de servidores, falhas de disco, interrupção de conexões de rede ou picos de tráfego [74, 82, 85].
4. **Tentar Refutar a Hipótese**: Buscar diferenças no estado estável entre o grupo de controle e o grupo submetido à falha [74, 82].

### Princípios Avançados
- **Construir Hipóteses sobre o Estado Estável**: Focar na saída sistêmica mensurável (vazão, taxa de erro, percentis de latência) em vez de validar aspectos internos [75, 84].
- **Simular Eventos do Mundo Real**: Priorizar falhas por impacto potencial ou frequência de ocorrência (hardware, software e eventos não-falha como auto-scaling) [76, 85].
- **Executar Experimentos em Produção**: Amostrar tráfego real é a única forma confiável de capturar a complexidade e os caminhos de requisição autênticos do sistema [76, 86].
- **Automatizar para Execução Contínua**: Tornar a experimentação um processo contínuo e contido em pipelines automáticos [77, 87].
- **Minimizar o Raio de Impacto (Blast Radius)**: É responsabilidade do engenheiro garantir que os impactos colaterais sejam minimizados e contidos [77, 87].

---

## 3. Integração com SRE (Site Reliability Engineering)
A prática de SRE desenvolvida pelo Google combina princípios de engenharia de software com operações de infraestrutura [38, 90, 92]. A engenharia do caos complementa o SRE de diversas formas:

- **SLIs, SLOs e Error Budgets**: O estado estável do caos é medido diretamente através dos *Service Level Indicators* (SLIs) e *Service Level Objectives* (SLOs) [31, 38, 90]. O *Error Budget* (orçamento de erros) fornece a margem aceitável para a realização de testes controlados em produção [93, 101].
- **Redução de Trabalho Braçal (Eliminating Toil)**: A automação de testes de resiliência e failover elimina tarefas operacionais manuais e repetitivas [38, 101].
- **DiRT (Disaster Resilience Testing)**: Prática desenvolvida pelo Google para testar continuamente a recuperação de desastres e prontidão operacional de sistemas e equipes [29].

---

## 4. Ecossistema de Ferramentas e Práticas de Mercado

### 4.1. Netflix Simian Army & Chaos Kong
- **Chaos Monkey**: Criado em 2010 pela Netflix durante sua migração para a nuvem AWS, encerra aleatoriamente instâncias EC2 em produção durante horário de expediente para forçar os desenvolvedores a projetarem aplicações que sobrevivam a quedas imprevistas de infraestrutura [2, 118, 119].
- **Latency Monkey**: Introduz atrasos artificiais na comunicação client-server RESTful para simular degradação sem derrubar o serviço [121].
- **Chaos Kong**: Evolução do Chaos Monkey projetada para simular a queda de uma Região inteira da AWS, validando o failover automatizado do tráfego global [3, 5].

### 4.2. AWS Fault Injection Service (AWS FIS)
Serviço totalmente gerenciado da AWS que permite criar e executar experimentos de injeção de falhas com controles e guardrails nativos de nuvem [143].
- **Componentes**:
  - **Ações**: Atividades executadas em recursos da AWS (parada de EC2, estresse de CPU, falhas de pods EKS/ECS, atrasos de conectividade cross-AZ) [145, 146].
  - **Alvos (Targets)**: Recursos identificados individualmente ou por tags/estados [145].
  - **Stop Conditions (Condições de Parada)**: Alarmes do Amazon CloudWatch que interrompem automaticamente o experimento se limites de segurança forem ultrapassados [144, 146].

### 4.3. Google Cloud & Chaos Toolkit
- **Chaos Toolkit**: Framework open-source modular escrito em Python com extensões para Google Cloud, Kubernetes e ToxiProxy [34, 35].
- **Receitas para GCP**: Simulação de cenários como quebra de comunicação entre Cloud Run e Cloud SQL usando ToxiProxy, ou simulação de falhas atrás de Application Load Balancers [35].

---

## 5. Tipos Comuns de Testes de Caos
- **Injeção de Latência**: Introdução controlada de atrasos de rede para testar *timeouts* e mecanismos de *circuit breaker* [56].
- **Injeção de Falhas de Infraestrutura**: Desligamento de VMs/containers, desconexão de interfaces de rede e encerramento de processos [56].
- **Geração de Carga e Estresse**: Envio de volumes de tráfego significativamente acima do normal para identificar gargalos de alocação de recursos [56].
- **Testes Canário**: Implementação de alterações ou experimentos em um pequeno subconjunto de usuários/servidores para limitar o raio de impacto [56, 57].
