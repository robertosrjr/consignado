# Rascunho — evolução de SRE, SLIs, SLOs e SLA na PoC

> **Status:** proposta para discussão; não aprova requisitos nem autoriza execução automática do plano.  
> **Data da análise:** 17/10/2026.  
> **Escopo:** revisão dos três documentos desta pasta, confrontada com código, configuração e evidências locais do repositório. Não houve consulta ao ambiente AWS nem alteração da aplicação nesta revisão.  
> **Público:** desenvolvimento, arquitetura, operação e responsável pelo produto.

## 1. Parecer

A ideia é adequada à PoC: medir o aceite na entrada separadamente da conclusão assíncrona e relacionar confiabilidade ao resultado do negócio. Isso permite demonstrar algo mais relevante que uma API rápida: **a proposta aceita continua rastreável, recebe uma decisão correta e não fica esquecida no caminho**.

A principal evolução deve ser a qualidade da comprovação. Hoje os textos misturam objetivos de carga, arquitetura desejada, funcionalidades existentes e resultados supostamente aprovados. Aplicar esses documentos diretamente como especificação de implementação poderia introduzir requisitos incompatíveis entre si e alterar componentes que já possuem contratos diferentes.

Recomendação: começar com três famílias de indicadores — aceite, resposta rápida e decisão dentro do prazo —, uma fonte confiável para acompanhar propostas pendentes e poucos exercícios reproduzíveis de falha. Consolidar a medição antes de assumir um SLA externo ou adicionar ferramentas.

**Primeiro incremento recomendado:** um contrato de medição e um relatório por execução do JMeter, reconciliando chamadas HTTP, propostas novas, duplicatas, decisões e pendências. Não é necessário esperar uma operação de produção para começar.

## 2. Documentos avaliados e contribuição de cada um

| Documento | O que merece ser preservado | Ajuste necessário |
|---|---|---|
| [Métricas de crédito](metricas-de-credito.md) | Aproxima a telemetria de volume financeiro, conversão, prioridade e qualidade do canal. | Separar KPIs comerciais de SLIs; uniformizar bruto/líquido e coortes; tratar regras de mercado e regulação como referências a validar. |
| [SRE no consignado](sre-no-consignado.md) | Explica bem por que HTTP 202 e dashboard verde não comprovam a conclusão do negócio. | Qualificar números como cenário simulado; corrigir deduplicação, previsões de drenagem e a descrição da propagação W3C. |
| [SDD SRE/SLI/SLO](sdd-sre-sli-slo-consignado-poc.md) | Organiza jornadas, metas, proteção do legado e observabilidade. | Reclassificar o documento como proposta; revisar arquitetura, fórmulas, referências e alegações de aprovação antes de utilizá-lo para gerar código. |

Os três documentos foram preservados. As correções abaixo são recomendações para uma revisão posterior deles.

## 3. Conceitos que devem orientar a revisão

SLI é a medição de uma característica do serviço; SLO é a meta dessa medição, com população e período definidos. Poucos indicadores relacionados à experiência do usuário são mais úteis que uma lista extensa de métricas de componentes. [Google SRE — Service Level Objectives](https://sre.google/sre-book/service-level-objectives/).

SLA acrescenta um compromisso com o usuário e consequências pelo descumprimento. Para esta PoC, faz sentido propor SLOs internos; chamar uma estimativa de fila de “SLA preditivo” confunde previsão operacional com acordo de serviço. [Google Cloud — SLI, SLO e SLA](https://cloud.google.com/blog/en/products/devops-sre/sre-fundamentals-sli-vs-slo-vs-sla).

| Item | Aplicação proposta na PoC |
|---|---|
| KPI comercial | Valor recebido, confirmado, rejeitado, ticket médio e conversão. Explica resultado; sozinho não prova confiabilidade. |
| SLI | Fração de solicitações elegíveis aceitas corretamente; fração de propostas com decisão correta dentro do prazo. |
| SLO | Meta quantitativa do SLI, para população, prazo e janela explicitamente definidos. |
| Orçamento de erro | Parcela de eventos ruins admitida pelo SLO. Orienta prioridades; não autoriza perda ou duplicação financeira. |
| Invariante de correção | Não produzir dois efeitos financeiros para a mesma operação canônica. Qualquer violação exige investigação. |
| Sinal de diagnóstico | CPU, taxa de chamadas, circuit breaker, profundidade de fila, duração de spans. Ajuda a explicar falhas. |
| SLA | Eventual compromisso externo, posterior à comprovação técnica e à definição comercial. |

Na PoC, `CONFIRMED` significa a resposta do **legado simulado**. Não comprova averbação em órgão pagador, liquidação real ou reserva real de margem.

## 4. Estado encontrado no projeto

Esta matriz descreve o conteúdo local inspecionado. Configuração no repositório não comprova configuração implantada ou serviço em execução.

| Capacidade | Evidência local | Limite atual |
|---|---|---|
| Aceite e idempotência técnica | `AcceptProposalService`, `DynamoDbInboxAdapter`, `ProposalController`; resposta nova ou `DUPLICATE_ACCEPTED`. | Persistência local em memória não demonstra durabilidade após reinício. Um 202 não comprova conclusão no legado. |
| Business Fingerprint v2 | Adaptador transacional, configuração e [documentação de implementação](../../../business-fingerprint-v2-implementacao.md). | Recurso desabilitado por padrão; ativação e recursos AWS são uma entrega separada. |
| Roteamento e consumo | Dispatcher, filas fast/standard, orçamento de capacidade, retries e circuit breaker no worker. | Execução distribuída e todas as janelas móveis de 1 s exigem comprovação própria. |
| Estado terminal do worker | `InMemoryProposalRepository`, eventos e observador de negócio. | Estado perdido no reinício; não equivale a Aurora, outbox durável ou coordenação entre réplicas. |
| Métricas de negócio | `business_vgv_received_brl_count/_sum`, `business_vgv_brl_count/_sum`, timer `business.time.to.settle`. | Contadores locais à JVM; timer registra somente propostas que chegaram à finalização observada. |
| Diagnóstico de backlog | `BacklogEstimator`, `worker_queue_depth`, `worker_processing_rate`, `effective_drain_time_seconds`. | Estimativas por amostras; conclusão de mensagem repetida também pode entrar na taxa do worker. |
| Métricas HTTP | Histogramas habilitados no starter e rota `POST /v1/proposals`. | Não veem chamadas que falham antes de chegar à aplicação; não possuem os rótulos de elegibilidade inventados no SDD. |
| Alertas | Sete regras em `consignado-sre.yml` e testes de regras no repositório. | Limiares estáticos, ainda sem política de orçamento de erro operacionalizada. |
| Entrega de alertas | Configuração nativa Alertmanager → SNS → SQS, com `send_resolved`. | Entrega em fila não comprova atendimento humano, reconhecimento e escalonamento. |
| Logs | JSON/ECS, correlação e customizador de mascaramento no starter. | Formato e mascaramento não comprovam retenção, busca centralizada ou ausência de todo dado sensível. |
| Traces | Propagação W3C API → Inbox → dispatcher → SQS → worker → legado implementada e testada localmente. | Visualização e entrega OTLP no Jaeger ainda precisam de validação; ver [guia de tracing local](../../../manuais/tracing-local.md). |
| Histórico de métricas | Compose define retenção padrão de 15 dias no Prometheus. | Não sustenta sozinho uma avaliação de 30 dias. Paradas e limpezas também removem evidências. |

O parent Maven atual declara **Java 25 e Spring Boot 4.1.1**. O Java 21 usado para abrir o JMeter é um requisito da ferramenta de carga, separado da versão da aplicação.

## 5. Crítica construtiva e correções prioritárias

### 5.1 A promessa de prazo precisa caber na capacidade

No cenário do SDD, assumindo que todas as entradas sejam **propostas novas**:

```text
Entradas:                       10.000/s × 300 s = 3.000.000 propostas
Saída ideal durante o pico:        180/s × 300 s =    54.000 propostas
Backlog ao fim do pico:                           2.946.000 propostas
Drenagem posterior, sem entradas: 2.946.000 / 180 = 16.366,7 s ≈ 4h33
Tempo mínimo desde o início:     3.000.000 / 180 = 16.666,7 s ≈ 4h38
```

Esse cálculo já é otimista: pressupõe processamento imediato, sem retries, indisponibilidade ou outros consumidores da capacidade. A configuração padrão do worker ainda aplica margem de segurança de 0,98 ao orçamento.

Não é possível prometer conclusão geral em 45 minutos para essa carga. A divisão 70/30 distribui a capacidade, sem ampliá-la. Se grande parte das chamadas for duplicata, o resultado muda; o experimento precisa declarar a fração de propostas canônicas novas.

**Aplicação:** descrever um envelope de carga com taxa de chamadas, taxa de novas propostas, duração do pico, mistura das lanes e amplificação por retries. Escolher conscientemente entre reduzir a admissão, ampliar capacidade real do destino ou aceitar prazo maior. Mais workers não aumentam o teto do legado.

### 5.2 Aceite HTTP e proposta nova têm denominadores diferentes

O SDD usa `outcome="RECEIVED"` e `eligible=true` no contador HTTP. Esses rótulos não correspondem à instrumentação HTTP encontrada. O resultado de negócio é registrado separadamente em `ingestion.accepted`, com valor normalizado para minúsculas.

Uma repetição aceita corretamente continua sendo sucesso do contrato HTTP, embora não crie outra proposta nem acrescente VGV. Excluir duplicatas do numerador e mantê-las no denominador faria a disponibilidade cair justamente quando a idempotência funciona.

**Aplicação:** separar chamadas HTTP elegíveis, propostas canônicas novas e tentativas duplicadas. Instrumentar explicitamente o contrato de elegibilidade antes de publicar consultas “oficiais” de SLO.

### 5.3 Deduplicação não é rejeição comercial

O fluxo atual retorna `DUPLICATE_ACCEPTED`, recuperando a aceitação original. Os textos também falam em `DUPLICATE_REJECTED` e em “100% em menos de 15 ms”. Isso mistura semântica, correção e desempenho.

**Aplicação:** manter a deduplicação como resposta idempotente; medir sua latência separadamente. Validar concorrência, payload divergente, janela temporal, reinício e timeout ambíguo. O número de duplicatas detectadas não revela quantas duplicatas escaparam: isso exige uma fonte independente de reconciliação.

O mock conta como `legacy_averbacoes_total{kind="duplicate"}` uma chamada repetida que recebe o mesmo resultado. Isso não significa um segundo efeito financeiro. O teste de integridade deve contar efeitos únicos, não exigir ausência de todo replay de transporte.

### 5.4 Conclusões rápidas podem esconder propostas eternamente pendentes

O timer `business.time.to.settle` é útil, mas é registrado apenas quando há finalização. Se 10 propostas terminarem rapidamente e 990 ficarem presas, observar somente esse timer pode produzir uma latência excelente.

**Aplicação:** criar um denominador a partir das propostas aceitas e avaliar cada uma ao vencer seu prazo, inclusive as que não chegaram ao worker. Preservar o histograma como diagnóstico dos finalizados; não apresentá-lo como cobertura de toda a jornada.

### 5.5 Drenagem prevista, idade e prazo realizado são medidas distintas

O estimador atual calcula `Q / μ`, profundidade dividida pela taxa de processamento observada. É uma aproximação de esvaziamento **sem novas chegadas**, não a idade da mensagem nem o prazo individual.

Com chegadas constantes e `μ > λ`, uma aproximação diferente seria `Q / (μ − λ)`. Com `μ <= λ`, não há previsão finita de esvaziamento nessas condições. Nenhuma dessas fórmulas substitui a distribuição de espera por lane ou considera sozinha prioridade, retries e in-flight.

**Aplicação:** mostrar separadamente idade desde o aceite, idade da fila, pendências vencidas e previsão com hipóteses visíveis. Incluir itens ainda na Inbox, mensagens invisíveis e mensagens em DLQ. Não somar a mesma profundidade global reportada por várias réplicas como se fossem filas diferentes.

DLQ por número de tentativas não impõe idade máxima de 60 minutos. Uma mensagem sem consumo pode envelhecer sem atingir `maxReceiveCount`. Encaminhar à DLQ também não equivale a concluir corretamente o negócio.

### 5.6 VGV recebido menos confirmado não é backlog financeiro exato

Há três problemas nos documentos:

1. “Recebido” é definido como valor bruto, enquanto “confirmado” aparece como líquido. As grandezas precisam representar o mesmo conceito monetário.
2. Confirmados nesta janela podem ter sido recebidos antes dela. Parte dos recebidos agora só finalizará depois.
3. Rejeições válidas deixam de ser pendência, mas não entram em confirmado. Valores e tickets podem divergir sem falha técnica.

**Aplicação:** preservar os gráficos atuais como visão de fluxo temporal e identificar a comparação por cores como sinal para investigação. Para conciliação, usar a mesma coorte de propostas únicas e a mesma base monetária:

```text
Recebidas da coorte = confirmadas + rejeitadas de negócio + ainda abertas
Valor recebido     = valor confirmado + valor rejeitado + valor ainda aberto
```

Essas igualdades pressupõem estados exclusivos e ausência de cancelamento, alteração de valor ou outros resultados. Se existirem, devem ganhar parcelas próprias. DLQ e falha técnica não resolvida continuam no grupo aberto, com subestado próprio. Tickets não obedecem a essa soma; comparar também a composição das coortes.

Prometheus usa valores de ponto flutuante e `increase()` estima deltas entre amostras. É adequado para tendência operacional; uma conciliação financeira exata deve partir de registros persistidos, com `BigDecimal` ou centavos inteiros, controles de acesso e deduplicação.

O contador `business.first.pass` atual divide os **confirmados** entre primeira tentativa e retentativa. Essa população não equivale automaticamente a “aprovados na primeira tentativa / todas as propostas recebidas”. Nome e fórmula do KPI precisam explicitar a diferença.

### 5.7 Arquitetura desejada não pode aparecer como implementação aprovada

Revisar no SDD:

| Afirmação | Correção sugerida |
|---|---|
| Java 21 / Spring Boot 3.3+ como stack atual | Atualizar para as versões declaradas no parent Maven, distinguindo JMeter. |
| Aurora e transactional outbox no worker | Registrar como opção futura; o repositório de propostas atual é em memória. |
| Aging com promoção automática após 10 minutos | Tratar como proposta: o `PriorityScorer` inspecionado é determinístico e não recebe tempo. Piso de capacidade e aging são mecanismos distintos. |
| Fórmula de score com pesos e multiplicador comercial específicos | Confrontar com `PriorityRules` e `PriorityScorer`; não substituir a regra existente sem decisão de produto. |
| Sharding pelo sufixo derivado do próprio fingerprint | Não resolve uma única chave quente: o mesmo fingerprint mantém o mesmo sufixo. Não usar sal aleatório para deduplicar sem preservar a unicidade global. |
| TTL de 24 h no exemplo | Confrontar com retenção padrão de sete dias e contrato v2; a janela precisa cobrir backlog e replays. |
| Streams eliminam todo risco de perda | Descrever entrega ao menos uma vez, retries, retenção, recuperação e monitoramento. Publicação e marcação não são uma única transação. |
| API publica o contexto diretamente no SQS | Descrever o caminho real: API grava contexto na Inbox; dispatcher recupera e publica; worker extrai. W3C é propagação; OTLP é exportação de telemetria. |
| Regras de margem, bloqueios e portabilidade já homologadas | Exigir fontes oficiais, escopo e testes antes de alegar cobertura. Esta análise não valida essas regras regulatórias. |
| DoD “APROVADO”, “PROD-REALITY” e pronto para geração automática | Substituir por estados acompanhados de evidência: proposto, implementado, testado localmente ou validado no ambiente. |

As citações numéricas como `[206]` e `[334]` não têm bibliografia identificável no próprio SDD. Substituí-las por fontes rastreáveis. Há também caracteres de controle na fórmula de `alpha`, que precisam ser corrigidos na revisão editorial.

### 5.8 A observabilidade também pode falhar

O projeto envia métricas ao Pushgateway a cada 10 s e o Prometheus coleta a cada 15 s, conforme os padrões encontrados. Portanto, `rate(...[1s])` não comprova um teto em todas as janelas móveis de um segundo; pode sequer ter amostras suficientes para ser calculado.

O `legacy_peak_window` do mock oferece uma evidência melhor do pico observado pelo gate durante a vida daquele processo, mas mede chamadas admitidas pelo gate. Não demonstra sozinho que todas as tentativas oferecidas, em todas as réplicas e reinícios, respeitaram o teto.

**Aplicação:** validar o limite com medição no destino, incluindo tentativas, rejeições por capacidade e retries; guardar o máximo do experimento. Verificar comportamento de bursts, relógios, divisão entre instâncias e alteração da quantidade de réplicas.

Pushgateway mantém séries depois de o produtor parar; `up=1` significa que a coleta do gateway funcionou. Manter a verificação de frescor e um inventário de instâncias esperadas. Para serviços permanentes, avaliar coleta direta por rede privada; manter o push enquanto for uma solução prática para a aplicação local.

Uma instância única de observabilidade é suficiente para aprender, mas não consegue notificar a própria perda total sem um observador independente. O desligamento planejado da EC2 não pode virar disponibilidade de 100% por ausência de dados.

## 6. Contrato inicial dos SLIs

### 6.1 Regras comuns

- **Usuário:** integrador que envia uma proposta e precisa saber se ela foi aceita e qual foi a decisão final.
- **Operação HTTP:** uma tentativa de chamada; **operação de negócio:** uma proposta canônica. Retries podem criar várias chamadas para a mesma proposta.
- **Dimensões limitadas:** ambiente, serviço, rota normalizada, lane e resultado de uma lista fechada. Sem IDs de proposta, fingerprint, CPF ou execução como labels permanentes.
- **Relógio:** instantes em UTC e sincronização entre processos; durações locais com relógio monotônico. Sinalizar timestamps ausentes ou incoerentes, sem fabricar latência zero.
- **Sem demanda:** resultado “sem eventos elegíveis”. **Sem cobertura de coleta:** resultado “medição incompleta”. Nenhum dos dois equivale a SLO cumprido.
- **PoC:** período de carga explicitamente declarado, seguido de observação até vencerem os prazos de todas as propostas. Resultados fora dele são apresentados separadamente.
- **Evolução para operação contínua:** janela móvel de 30 dias é uma opção a discutir. Exige retenção superior à janela, margem para recuperação e cobertura verificável. Um teste de cinco minutos não comprova disponibilidade mensal.

### 6.2 SLI A — aceite correto das chamadas elegíveis

```text
SLI A = chamadas elegíveis com aceite correto / chamadas elegíveis
```

**Boa:** resposta 202 recebida pelo cliente, associada à proposta nova persistida ou à aceitação original recuperada corretamente. Uma duplicata aceita entra como sucesso HTTP, sem novo VGV.

**Ruim:** 5xx, timeout ou erro de conexão em uma tentativa elegível; 202 sem evidência de aceite íntegro; rejeição técnica indevida. Uma gravação concluída após o timeout não transforma retroativamente aquela tentativa HTTP em sucesso. O retry pode ser outra tentativa boa para a mesma proposta canônica.

**Elegibilidade proposta:** requisição conforme contrato, credenciais válidas e dentro da cota e do envelope de carga acordados. Erros esperados por payload, autenticação ou cota ficam em indicadores próprios. Um 429 para cliente dentro da cota é falha de serviço, não exclusão automática. Não excluir falha de dependência só porque ocorreu fora do processo da API.

**Medição inicial:** JMeter/JTL com massa conhecida, confrontado com Inbox e contadores de resultado. Métricas HTTP no servidor são uma segunda perspectiva, com suas lacunas declaradas. Fora do teste, será necessário um ponto de observação na borda/cliente para detectar falhas anteriores à aplicação.

**Meta candidata:** 99,9% no experimento e envelope definidos. Hipótese de engenharia, ainda sem homologação ou compromisso mensal.

### 6.3 SLI B — resposta de aceite dentro do limite

```text
SLI B(T) = chamadas elegíveis com aceite correto e duração <= T
           / chamadas elegíveis
```

Um erro rápido não é evento bom. Medir no cliente no experimento; separar conectividade, TLS, autenticação e espera da aplicação para diagnóstico. Não comparar silenciosamente latência de cliente com tempo interno do servidor.

**Metas candidatas herdadas:** 95% em até 100 ms e 99% em até 200 ms. Não são resultados comprovados. Manter p95/p99 para diagnóstico, sem calcular média dos percentis de cada instância.

**Implementação futura:** histogramas com buckets explícitos nos limites escolhidos, ou contadores de resultado já classificados. Histograma HTTP existente não implica bucket exato em 100 ms ou rótulos de elegibilidade. Confirmar a exposição real antes de escrever PromQL; somar buckets entre instâncias antes de calcular quantis.

### 6.4 SLI C — decisão correta dentro do prazo da proposta

Este é o principal avanço da PoC: medir o compromisso iniciado no aceite, sem deixar pendências desaparecerem do denominador.

Para uma proposta canônica `i`:

```text
a_i = instante do aceite original
T_i = prazo definido pela classe de serviço
d_i = a_i + T_i

População da janela [t-W, t): propostas com d_i nessa janela
Boa: decisão correta persistida até d_i
Ruim: sem decisão correta até d_i, inclusive pendente, falha técnica ou DLQ

SLI C = propostas boas / propostas com prazo vencido na janela
```

Essa escolha é uma **coorte por vencimento**, não uma divisão entre contadores de entrada e saída do mesmo intervalo. O relatório também apresenta as propostas aceitas cujo prazo ainda não venceu.

**Decisão correta:** confirmação válida ou rejeição de negócio esperada e fundamentada. Rejeição causada por erro interno, serialização inválida ou indisponibilidade não é boa. O código atual converte uma rejeição do legado em estado `FAILED`; será necessário distinguir semanticamente rejeição comercial de falha técnica na projeção de medição.

**Regras de contagem:**

1. Cada proposta canônica recebe uma classificação por prazo; retries e mensagens repetidas não acrescentam denominador.
2. Uma finalização atrasada resolve a pendência, mas permanece ruim para o prazo original.
3. Preservar a classe e o prazo usados na avaliação; eventual promoção de prioridade não deve apagar uma violação anterior.
4. Propostas que nunca chegaram ao dispatcher também entram. Até existir classificação de lane, mostrá-las como não roteadas e cobrar um prazo global definido; não excluí-las silenciosamente.
5. Separar instante da decisão de atraso na coleta dessa evidência. Definir tolerância e regra de revisão de eventos atrasados, com trilha de auditoria.

**Metas candidatas do brainstorming:** fast: 95% até 2 min; standard: 90% até 45 min. Somente avaliar após definir mistura de tráfego e demonstrar viabilidade de capacidade. Não aprovar essas metas para o lote de três milhões descrito anteriormente.

**Dependência:** registro persistido do aceite, do estado terminal e do prazo, com reconciliação independente do sucesso do worker. Um avaliador periódico deve classificar vencimentos mesmo quando não houver consumo de fila. Sua própria indisponibilidade gera atraso de medição e alerta de cobertura.

Os buckets atuais de `business.time.to.settle` são 30 s, 1 min, 5 min, 15 min e 30 min. Não incluem exatamente 2 min e 45 min. Adicioná-los ajuda no diagnóstico dos finalizados, mas não substitui o avaliador de pendências.

### 6.5 Integridade: critérios separados do orçamento de disponibilidade

No escopo declarado do teste, exigir:

- Nenhum efeito financeiro adicional para a mesma operação canônica.
- Toda proposta reconhecida como aceita pode ser localizada e reconciliada após recuperação.
- Nenhuma transição terminal regressiva e nenhuma confirmação sem evidência do legado.
- Retenção de idempotência compatível com espera, tentativas, DLQ e recuperação.

Esses são critérios de correção para aprovar o experimento. “Zero violações observadas” não prova uma garantia universal de 100%. Falha de integridade bloqueia a evolução daquele cenário e exige investigação, mesmo com disponibilidade acima da meta.

## 7. Como medir sem transformar métricas em banco de negócio

### 7.1 Primeiro: verificador independente por execução

Criar um relatório fora do caminho crítico que utilize a massa/JTL, os aceites persistidos e os resultados do legado/worker. IDs podem existir nesse relatório controlado e em registros de auditoria; não precisam virar labels do Prometheus.

O relatório deve conter versão do código/configuração, modo de idempotência, horários, mistura de carga, contagens elegíveis, falhas, duplicatas, novas propostas, decisões no prazo, decisões tardias e pendências. Guardar hashes ou referências dos artefatos de evidência, sem payloads pessoais.

Concluir a carga não conclui a avaliação: aguardar os prazos restantes e executar a reconciliação. Carga incompatível com capacidade deve ser identificada como tal, sem esconder a cauda por interrupção precoce do teste.

### 7.2 Depois: projeção operacional persistida

Para operação contínua, escolher uma implementação pequena de estado/eventos duráveis do worker e uma projeção de vencimentos. A decisão entre DynamoDB e banco relacional merece avaliação própria; não há necessidade de introduzir Aurora só porque aparece no SDD.

Persistir classificação e proteger atualizações concorrentes. Se a gravação do resultado e a exportação de contador puderem divergir, usar reconciliação ou publicação transacional com consumidor idempotente. Não presumir que gravar no banco e incrementar um contador em memória formam uma transação.

**Esquema de métricas proposto, ainda inexistente:** contagem de avaliações elegíveis por `sli` e `verdict=good|bad`, pendências por lane, pendências vencidas e atraso do avaliador. O denominador pode ser a soma de boas e ruins da mesma população. Documentar como replays e reinícios não duplicam avaliações.

Não publicar agora uma consulta executável sobre esse esquema. Primeiro validar nomes, tipos, dimensões, frescor e comportamento de ausência/reset na exposição real.

### 7.3 Papel de cada sinal

| Sinal | Uso na evolução | Evidência mínima |
|---|---|---|
| Métricas | SLI agregado, orçamento, tráfego, latência, erro e saturação. | Numerador/denominador conhecidos, buckets adequados, cardinalidade limitada e testes de ausência/reset. |
| Logs | Explicar transições e falhas com contexto técnico seguro. | Evento estruturado, correlação, ausência de segredo/PII e procedimento de consulta/retenção definido. |
| Traces | Localizar tempo e falha entre API, banco, fila, worker e legado. | Uma jornada no Jaeger, retry com span próprio, hierarquia/links corretos e inspeção de atributos. |
| Estado/auditoria | Conciliação de operações, valores e vencimentos. | Persistência, estados exclusivos, unicidade, recuperação e controles de acesso. |

A busca centralizada de logs deve ser escolhida conforme uma necessidade demonstrada; não é pré-requisito para iniciar a medição local.

Tracing amostrado explica exemplos, mas não é o denominador dos SLIs. Usar 100% em validação pequena e dimensionar sampling para carga. A indisponibilidade do exportador não deve impedir o aceite de negócio.

## 8. Orçamento de erro e alertas aplicáveis

### 8.1 Cálculo por eventos

```text
N = eventos elegíveis da janela
B = eventos ruins da janela
S = meta do SLO, entre 0 e 1

Orçamento total em eventos = N × (1 − S)
Orçamento consumido        = B / [N × (1 − S)]
Burn rate                 = (B / N) / (1 − S)
```

Exemplo interno: 1.000.000 de chamadas elegíveis com meta de 99,9% permitem 1.000 eventos ruins; 250 consomem 25% desse orçamento. Não converter esse orçamento diretamente em minutos: ele é baseado em chamadas, e o pico concentra o impacto. No SLI C, a população é de propostas com prazo vencido, não tentativas HTTP.

### 8.2 Evolução dos alertas

Manter inicialmente as sete regras estáticas e seus runbooks. Elas são úteis para a PoC, mas não demonstram cumprimento de SLO. O piso de 100 requisições reduz sensibilidade em testes pequenos e também pode esconder uma falha completa em baixo tráfego.

Para um SLO operacional de 30 dias, avaliar posteriormente os pares de janelas 1 h/5 min com burn rate 14,4 e 6 h/30 min com burn rate 6. A condição de cada par precisa ocorrer nas duas janelas. São pontos de partida; os parâmetros devem ser calibrados ao tráfego e à capacidade de resposta. [Google SRE Workbook — Alerting on SLOs](https://sre.google/workbook/alerting-on-slos/).

Na PoC intermitente, começar com resultado por execução e alerta separado de pendência vencida. Não copiar janelas mensais para um teste de poucos minutos. Em baixo tráfego, usar também sondas sintéticas identificadas e frescor, mantendo seus resultados separados do tráfego de usuário.

| Condição | Resposta proposta |
|---|---|
| Efeito financeiro duplicado ou aceite sem registro recuperável | Interromper aprovação do experimento; preservar evidência e investigar integridade. |
| Propostas vencidas/idade excessiva por lane | Investigar impacto, roteamento, retries e capacidade; acionar responsável conforme escopo do teste. |
| Crescimento de DLQ | Abrir investigação de causa e recuperação; nunca purgar/reprocessar automaticamente para melhorar painel. |
| Consumo rápido e sustentado de orçamento | Notificação urgente quando existir operação e responsável disponíveis. |
| Degradação lenta | Item de melhoria com prazo e dono. |
| Coleta, avaliador ou instância esperada ausente | Alerta de perda de cobertura; compliance fica desconhecido. |

Todos os novos alertas devem ter dono, ação, runbook, contexto de impacto e teste de firing/resolved. Usar a rota existente SNS → SQS; definir consumidor, deduplicação por episódio, reconhecimento e escalonamento antes de chamá-la de canal de plantão. A rota padrão atual é `blackhole`: labels de roteamento também precisam de teste.

Silêncios planejados reduzem notificações; não devem apagar eventos ruins do cálculo. O calendário dos experimentos deve ser explícito, sem retirar janelas problemáticas depois de observar o resultado.

### 8.3 Política simples de decisão

Proposta para a PoC: com integridade preservada e metas atendidas, avançar o cenário; com orçamento esgotado ou medição incompleta, priorizar recuperação da capacidade de comprovar confiabilidade antes de elevar carga ou declarar homologação. Falhas relevantes geram um registro sem culpabilização, com causa, impacto e ação verificável.

A política precisa determinar o que muda quando a meta falha; um percentual sem consequência operacional vira apenas outro painel. O exemplo de política do Google é uma referência para essa decisão, não uma obrigação de copiar seu processo de releases. [Google SRE Workbook — Error Budget Policy](https://sre.google/workbook/error-budget-policy/).

## 9. Plano incremental

Estimativas de esforço são relativas e precisam ser refinadas ao iniciar cada etapa. Papéis abaixo são responsabilidades propostas, sem atribuição automática a pessoas.

| Etapa | Entrega e locais prováveis | Dependência | Critério de conclusão | Responsável proposto |
|---|---|---|---|---|
| **P0 — Contrato e correções documentais** | Glossário, matriz real/proposto, elegibilidade, prazos, coortes e envelope de carga. Revisão dos três documentos desta pasta. | Decisões da seção 12. | Não há aprovação sem evidência nem meta incompatível com a capacidade declarada. | Produto + desenvolvimento. |
| **P1 — Experimento mensurável** | Relatório do JMeter e reconciliação de massa, Inbox e resultados; guardar manifesto e evidências. Scripts de carga e módulos ingestion/worker/mock. | P0. | Nova, repetida, rejeitada, atrasada e pendente produzem contagens esperadas em massa pequena conhecida. | Desenvolvimento + qualidade. |
| **P2 — Estado e vencimentos recuperáveis** | Implementação durável de `ProposalRepositoryPort`; classificação de resultado, prazo e avaliador independente. | P0; usar P1 como oráculo de validação. | Reiniciar worker/avaliador não perde propostas nem duplica avaliações; itens não roteados e DLQ continuam visíveis. | Desenvolvimento + arquitetura. |
| **P3 — Instrumentação e painéis de SLO** | Métricas do contrato, buckets, recording rules e painel versionado; qualificar comparações de VGV. `applications/observability/grafana/dashboards` e configuração Prometheus. | P1; SLI C operacional depende de P2. | Painel coincide com reconciliação; ausência/reset não viram sucesso; filtros não misturam ambientes. | Desenvolvimento + operação. |
| **P4 — Alertas e resposta** | Alertas de vencimento/DLQ/cobertura, burn rate quando aplicável, runbooks e consumidor SNS/SQS. | P3, cobertura histórica e responsáveis definidos. | Falha controlada gera firing, entrega, reconhecimento e resolved; agrupamento e silêncios preservam a contagem do SLO. | Operação + desenvolvimento. |
| **P5 — Carga e falhas progressivas** | Executar matriz da seção 10 com limites do experimento e relatório reproduzível. | P1 para medição; P2 para alegações de recuperação; P4 para exercício operacional. | Limites e regressões demonstrados com evidência; resultados válidos somente no ambiente testado. | Qualidade + desenvolvimento. |
| **P6 — Consolidação operacional** | Retenção compatível, monitor independente, rotina de revisão de SLO, recuperação e custo. Avaliar SLA apenas se houver necessidade externa. | Resultados de P5 e necessidade de serviço contínuo. | Metas e compromissos revisados por produto/operação, com dados e procedimento de resposta. | Produto + operação. |

**Esforço esperado:** P0 é principalmente definição; P1 e parte de P3 reaproveitam bastante código. P2 é a maior mudança arquitetural. P4 não é só adicionar YAML: depende de existir quem processe e atenda a notificação. A validação do tracing no Jaeger pode ocorrer em paralelo a P1, quando o ambiente estiver disponível.

Começar com persistência e avaliador simples, com consultas limitadas/indexadas; evitar varredura completa da Inbox a cada scrape. Não colocar conciliação financeira pesada na requisição HTTP ou no endpoint de métricas.

## 10. Matriz mínima de experimentos

| Experimento | O que demonstra | Resultado esperado |
|---|---|---|
| Propostas novas válidas em baixa carga | Contagem e caminho feliz. | 202, uma proposta canônica por entrada nova, decisão rastreável e conciliação exata da massa. |
| Repetição técnica e payload divergente | Contrato idempotente e conflito. | Repetição recupera aceite sem novo VGV; alteração incompatível é diferenciada, sem efeito financeiro adicional. |
| Repetição semântica com v2 habilitada | Contrato Business Fingerprint. | Chaves técnicas distintas convergem somente conforme as regras canônicas da v2; comparar com modo desabilitado. |
| Legado lento ou indisponível | Backpressure, orçamento e prazo. | Entrada e conclusão se degradam de formas distinguíveis; circuito/retries visíveis; pendências vencidas contam como ruins. |
| Algumas propostas nunca finalizam | Evita viés dos sobreviventes. | Timer dos concluídos pode ficar baixo, mas SLI C e lista de pendências detectam a falha. |
| Timeout após efeito no legado | Ambiguidade e replay. | Repetição usa a mesma identidade; não produz outro efeito; replay não é confundido com duplicação financeira. |
| Queda após publicar antes de marcar despacho | Entrega ao menos uma vez. | Reentrega esperada, tratamento idempotente e nenhuma duplicação do resultado da conciliação. |
| Reinício de worker e execução com duas réplicas | Persistência e concorrência. | Após P2, estados/avaliações sobrevivem e não duplicam; medir o teto global no destino. |
| Tráfego contínuo nas duas lanes | Justiça entre classes. | Piso da standard demonstrado, idade observada por lane e ausência de starvation no cenário; não presumir promoção por aging. |
| Mensagem inválida/DLQ e recuperação | Visibilidade de falha duradoura. | Proposta permanece contabilizada; replay controlado não apaga violação de prazo anterior. |
| Parada do push, scrape e avaliador | Confiabilidade da medição. | Cobertura incompleta fica explícita; alerta próprio; painel não permanece saudável por séries antigas. |
| Jornada exportada ao Jaeger | Entrega e correlação de traces. | Serviços, contexto, retry e ausência de dados sensíveis verificados; resultado não usado como totalizador de propostas. |
| Rampas de carga até o envelope | Capacidade e gargalo real. | Registrar taxa oferecida/aceita/nova, latência, rejeições, backlog, pico no destino e recursos do gerador. |

O cenário de 10.000 req/s só deve ser a última rampa, com autorização operacional já aplicável ao ambiente e gerador dimensionado. Um gerador saturado ou teste fechado que reduz a demanda quando o serviço fica lento pode esconder a sobrecarga; registrar a taxa realmente oferecida e os atrasos do próprio gerador.

Falhas esperadas nos cenários de caos não são removidas para deixar o SLO verde. O relatório explica a injeção, o impacto observado e o comportamento de recuperação.

## 11. Evidências necessárias para elevar a maturidade

Adotar uma escala simples para cada requisito:

```text
Proposto → implementado → testado localmente → validado no ambiente-alvo
```

Cada passagem exige artefato e escopo. Teste unitário comprova uma regra; integração local comprova conexões daquele ambiente; carga demonstra desempenho nas condições registradas. Nenhum deles, isoladamente, comprova uma operação contínua de 30 dias.

Checklist do pacote de evidências de uma execução:

- [ ] Revisão do código e configuração utilizadas; recursos e quantidade de instâncias.
- [ ] Massa, taxa oferecida, mistura de novas/duplicatas e distribuição fast/standard.
- [ ] Horários, cobertura da coleta, reinícios e falhas injetadas.
- [ ] Totais de eventos elegíveis, bons e ruins, com fórmula e população.
- [ ] Coorte de propostas conciliada, incluindo abertas, atrasadas e DLQ.
- [ ] Evidência independente do teto de chamadas no destino e efeitos únicos.
- [ ] Exercício de alerta com entrega e reconhecimento, quando incluído no escopo.
- [ ] Limitações conhecidas, custo aproximado, ações e responsável pela revisão.

No artigo, separar explicitamente o que foi desenhado, implementado e observado. A PoC ganha credibilidade ao mostrar um SLI ruim que detecta uma falha real, com recuperação demonstrada, em vez de publicar percentuais altos sem denominador verificável.

## 12. Decisões pendentes antes de implementar

1. **Experiência esperada:** é suficiente uma decisão correta, inclusive rejeição comercial, ou há outro compromisso com o cliente? Conversão financeira deve continuar separada da confiabilidade técnica.
2. **Carga elegível:** qual taxa de propostas realmente novas, duração do pico, mistura de lanes e cota por integrador queremos sustentar?
3. **Prazos:** manter 2/45 minutos em cenários limitados ou escolher metas compatíveis com outro envelope? Qual prazo cobra propostas ainda não roteadas?
4. **Fonte persistida:** onde registrar decisão, identidade canônica e vencimento? Qual janela de idempotência/recuperação precisa ser garantida?
5. **Escopo temporal:** apenas experimentos agendados ou operação contínua? Que retenção e observador independente esse escopo exigem?
6. **Resposta:** quem consome SNS/SQS, reconhece o incidente e conduz recuperação? Qual cobertura operacional será oferecida?

Essas decisões não impedem começar P0/P1 com massa pequena e relatório explícito. Impedem apenas transformar hipóteses em garantias externas ou aprovações de produção.

## 13. Fontes e pontos de navegação

### Referências externas consultadas

- [Google SRE Book — Service Level Objectives](https://sre.google/sre-book/service-level-objectives/): escolha de indicadores e distinção entre objetivo e acordo.
- [Google Cloud — SRE fundamentals: SLIs vs SLAs vs SLOs](https://cloud.google.com/blog/en/products/devops-sre/sre-fundamentals-sli-vs-slo-vs-sla): artigo localizado no endereço atual; a URL fornecida inicialmente não respondeu na consulta.
- [Google SRE Workbook — Implementing SLOs](https://sre.google/workbook/implementing-slos/): especificação do indicador, forma de medição e revisão iterativa. A proposta deste rascunho usa poucos SLIs e começa com um experimento verificável, adaptando essa orientação ao projeto.
- [Google SRE Workbook — Alerting on SLOs](https://sre.google/workbook/alerting-on-slos/): burn rate, múltiplas janelas e limitações de baixo tráfego.
- [Google SRE Workbook — Example Error Budget Policy](https://sre.google/workbook/error-budget-policy/): orçamento como instrumento de priorização.

### Evidências locais para a implementação futura

Todos os caminhos abaixo são relativos à raiz do repositório, salvo os links de navegação:

- [Estado atual](../../../estado-atual.md), [auditoria de idempotência](../../../auditoria-idempotencia.md), [Business Fingerprint v2](../../../business-fingerprint-v2-implementacao.md) e [tracing local](../../../manuais/tracing-local.md).
- [Parent Maven](../../../../applications/pom.xml), configurações `ingestion-api/src/main/resources/application.yml` e `worker/src/main/resources/application.yml`, dentro de `applications`.
- API: `MeteredAcceptProposalUseCase`, `ProposalController`, `DynamoDbInboxAdapter` e `BusinessFingerprintInboxAdapter`.
- Dispatcher: `PriorityScorer`, `PriorityRules` e serviço de despacho.
- Worker: `ProcessProposalService`, `InMemoryProposalRepository`, `BusinessMetrics`, `WorkerMetrics`, `BacklogEstimator` e `SqsLaneQueueAdapter`.
- Mock legado: `AverbacaoController`, `AverbacaoLedger`, `CapacityGate` e `LegacyMetrics`.
- [Dashboards versionados e fórmulas](../../../../applications/observability/grafana/dashboards/README.md).
- [Regras SRE](../../../../infrastructure/Configuration/observability/config/prometheus/rules/consignado-sre.yml), [testes das regras](../../../../infrastructure/Configuration/observability/config/prometheus/tests/consignado-sre.test.yml) e [runbooks](../../../../infrastructure/Configuration/observability/ALERTAS.md).
- [Configuração de coleta](../../../../infrastructure/Configuration/observability/config/prometheus/prometheus.yml), [Alertmanager](../../../../infrastructure/Configuration/observability/config/alertmanager/alertmanager.yml) e [Compose da observabilidade](../../../../infrastructure/Configuration/observability/compose.sem-elasticsearch.yaml).

Os números de capacidade e as metas sugeridas neste rascunho são cálculos e hipóteses específicos da PoC. Não são garantias do Google, da AWS ou do mercado de crédito.
