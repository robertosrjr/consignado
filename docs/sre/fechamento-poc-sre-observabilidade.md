# Fechamento da PoC de SRE e observabilidade

**Data do fechamento:** 18/10/2026  
**Resultado:** concluída, com os objetivos centrais atingidos e limitações conhecidas  
**Escopo:** SRE e observabilidade da PoC de crédito consignado

## Conclusão executiva

A PoC demonstrou que a jornada de uma proposta pode ser observada além da resposta HTTP. Foi possível relacionar aceite, fila, processamento, chamada ao legado e decisão final; medir quantidades e valores de negócio; investigar falhas por logs e traces; acionar alertas; e conferir a recuperação de propostas em experimentos controlados.

O principal resultado não é a existência de dashboards. É a capacidade de responder, com evidências, perguntas como:

- Quais propostas foram aceitas, confirmadas, rejeitadas ou permaneceram pendentes?
- Quanto valor entrou e quanto foi confirmado para a mesma execução?
- A API respondeu rápido e corretamente?
- A decisão ocorreu dentro do prazo definido?
- Em qual etapa uma jornada falhou ou esperou?
- O mecanismo de resiliência protegeu o legado e preservou as propostas?
- O alerta realmente detectou a condição observada?

A conclusão formal é:

> **A PoC de SRE e observabilidade está concluída. Seus objetivos centrais foram atingidos, as evidências foram preservadas e as lacunas encontradas passam a compor o backlog de evolução para produção.**

Este fechamento não homologa capacidade de produção, não estabelece SLA externo e não garante efeito financeiro exatamente uma vez em um ambiente real.

## O que foi entregue

### Observabilidade técnica e de negócio

- Instrumentação com Micrometer e exportação de métricas ao Prometheus por Pushgateway.
- Seis dashboards Grafana versionados para ingestão, proteção do downstream, backlog, negócio, operação SRE e experimentos.
- Métricas de propostas novas, confirmações, rejeições, valores, ticket médio, primeira passagem, tempo até decisão, filas, vazão e circuit breaker.
- Logs estruturados e traces OpenTelemetry recebidos no Jaeger.
- Jornadas locais verificadas entre `ingestion-api`, dispatcher, worker e mock legado.

### Medição de confiabilidade

- Contrato inicial dos SLIs de aceite correto, aceite rápido e decisão no prazo.
- Metas candidatas declaradas por população e janela, sem apresentá-las como SLA.
- Captura e relatório conciliado por experimento, distinguindo proposta única, tentativa, replay, decisão tardia, pendência e cobertura incompleta.
- Totais conciliados de quantidade, valor e ticket médio para execuções controladas.
- Política explícita para ausência de eventos e falta de cobertura, evitando apresentar ambos como 100% saudável.

### Alertas e resposta

- Sete regras SRE versionadas para coleta indisponível, ausência de push, erros e latência de ingestão, worker parado, drenagem elevada e indisponibilidade do legado.
- Runbooks com condição, persistência, interpretação, investigação e limites de cada alerta.
- Alertmanager configurado para publicar no SNS, com encaminhamento por assinatura à SQS e sem Lambda nessa rota.
- Testes das regras com `promtool`, incluindo firing, resolução, baixo tráfego, métricas antigas e ausência de dados.
- Exercício real de pausa do worker com alerta pending/firing, publicação e recuperação observadas até o SNS.

### Resiliência exercitada

- Retry de erro 503 do legado observado no mesmo trace até a confirmação da proposta.
- Timeout ambíguo com execução tardia e tentativa posterior observado no Jaeger.
- Circuit breaker do worker exercitado localmente: abertura após falhas, bloqueio de novos despachos, sondagem automática e recuperação das vinte identidades, sem DLQ no ensaio.
- Idempotência preservada nos reenvios utilizados nos exercícios.
- Mecanismo controlado de falha do mock com autenticação, expiração automática e restauração independente do sucesso do cliente.

## Evidências principais

| Exercício | Evidência obtida | Resultado e limite |
|---|---|---|
| Pausa do worker | Dez propostas permaneceram na fila durante a pausa; alerta real; dez confirmações depois da retomada. | Comprovou detecção e recuperação com o mesmo processo. Não comprovou recuperação após crash. |
| L1 — erro transitório | Cinco propostas novas e cinco reenvios; uma chamada 503 seguida de 200; cinco confirmações e R$ 5 mil conciliados. | Recuperação funcional aprovada. O relatório SRE marcou FAIL porque 9/10 chamadas atenderam aos limiares de 100 e 200 ms, abaixo das metas de 95% e 99%. |
| L2 — timeout ambíguo | Cliente do worker terminou após aproximadamente 3 s; servidor continuou por cerca de 4 s; retry posterior retornou 200. | A sequência foi observada no trace. A conciliação final desse experimento permaneceu incompleta e foi preservada como tal. |
| L3 — circuit breaker | Vinte falhas abriram o circuito; cem passos adicionais não despacharam; HALF_OPEN automático e fechamento após cinco sucessos; vinte identidades recuperadas. | Prova local com HTTP real e fila em memória. Validação integrada com SQS ficou fora do fechamento. |
| Alerta do legado | Testes de regra com backlog e telemetria recente. | OPEN contínuo dispara; alternância OPEN/HALF_OPEN não completa os 2 min e pode não alertar. Lacuna registrada. |
| Tracing | Jornadas com API, dispatcher, worker e mock; retries e relações entre spans verificadas. | Validado no ambiente local. Dispatcher Lambda no runtime AWS permanece fora da comprovação. |

Os exercícios estão documentados em [falha do worker](exercicio-falha-worker.md), [L1](exercicio-falha-legado-l1.md), [L2](exercicio-falha-legado-l2.md) e [L3](exercicio-circuit-breaker-l3.md).

## Ganhos para o negócio

O aceite HTTP deixou de ser tratado como sinônimo de conclusão. A PoC passou a separar entrada, processamento e decisão, preservando a identidade da proposta entre reenvios e retries.

O negócio ganhou uma leitura mais útil:

- Quantidade e valor recebidos não são somados novamente em um replay.
- Confirmação e rejeição são decisões de negócio distintas de uma falha técnica.
- Uma decisão tardia resolve a pendência, mas não apaga a violação do prazo.
- Diferença entre recebido e confirmado numa janela não é automaticamente interpretada como valor perdido; a conclusão depende da conciliação da mesma coorte.
- Circuit breaker, filas, retries, limites de vazão e idempotência formam a proteção; métricas e traces verificam como essa proteção se comportou.

O ganho central foi substituir “parece que voltou” por afirmações verificáveis: quais propostas voltaram, quais valores foram conciliados, qual prazo falhou e qual hipótese ainda não foi comprovada.

## O que esta PoC não comprova

- Sustentação das metas de 10.000 requisições por segundo na entrada e 180 conclusões por segundo sob uma rodada de carga homologada.
- Disponibilidade mensal, SLA externo ou orçamento de erro operacional contínuo.
- Efeito financeiro exatamente uma vez em produção.
- Recuperação depois de crash ou reinício com perda do estado em memória.
- Persistência durável de todo o estado do worker e dos emuladores locais.
- Jornada do dispatcher Lambda e propagação completa no runtime AWS.
- Redrive seguro de DLQ exercitado de ponta a ponta.
- Alta disponibilidade da observabilidade ou alerta externo para queda da EC2/Prometheus.
- Atendimento humano dos alertas, incluindo consumidor SQS, reconhecimento, escalonamento e encerramento do incidente.
- Business Fingerprint ativo e validado nos mesmos exercícios operacionais; sua implementação permanece uma capacidade separada e opt-in.

## Backlog recomendado para evolução

As atividades abaixo não impedem o fechamento da PoC. Elas são o caminho para uma fase posterior de endurecimento e preparação para produção:

1. Corrigir o detector de indisponibilidade sustentada do legado para cobrir a alternância OPEN/HALF_OPEN sem confundir ausência de chamadas com sucesso.
2. Implementar métricas de idade da mensagem mais antiga, profundidade/frescor da DLQ e sucesso do amostrador.
3. Exercitar redrive individual, preservando identidade, envelope e contexto de correlação.
4. Persistir o estado necessário para demonstrar recuperação após crash e reinício.
5. Definir o consumidor da SQS de alertas, responsável, confirmação de atendimento e escalonamento.
6. Validar o tracing no runtime AWS, incluindo dispatcher Lambda e encerramento/flush da telemetria.
7. Executar carga progressiva com envelope declarado, critérios de interrupção e conciliação de negócio.
8. Criar monitoramento externo da stack e revisar retenção, disponibilidade e recuperação da observabilidade.
9. Evoluir para alertas por consumo de orçamento de erro quando houver coleta contínua e SLOs aprovados.

## Estado no encerramento

- Serviços Java locais encerrados.
- Containers e serviços Docker parados.
- EC2 de observabilidade confirmada como `stopped`.
- Volumes e evidências preservados.
- Nenhum commit ou push executado pelo assistente; o usuário mantém essa etapa sob seu controle.

O [artigo final da jornada](artigo-o-seu-sre-mostra-o-que-v2.md) apresenta os mesmos aprendizados em formato editorial. O [guia SRE](README.md) permanece como índice técnico das métricas, dashboards, alertas e experimentos.

## Decisão de encerramento

Ficam encerradas a construção exploratória e a validação da PoC de SRE e observabilidade. Os itens não executados foram deliberadamente reclassificados como backlog, porque dependem de uma nova fase com objetivos de produção, durabilidade, escala ou operação humana.

Qualquer retomada deve começar por um novo incremento, com escopo e critérios próprios, reutilizando as evidências desta PoC sem reclassificar resultados incompletos como sucesso.
