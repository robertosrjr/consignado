# Contrato de medição SRE — experimento v1

**Versão:** `poc-v1`. **Estado:** implementado para avaliação offline; metas experimentais, sem homologação de capacidade ou SLA externo. Revisão: 17/10/2026.

Este contrato define a população, as fontes e os resultados calculados por `SreReport`, no módulo `local-env`. O [guia de execução](relatorio-local.md) explica os arquivos e a demonstração; a [captura local](captura-local.md) integra JTL, Inbox e snapshot do worker em experimentos controlados. A projeção operacional persistida permanece uma etapa posterior.

## Fontes e identidade

O avaliador recebe um manifesto e evidências normalizadas de **cliente**, **Inbox** e **decisão**. Não consulta AWS, Grafana ou aplicação, não lê tokens e não deduz estado terminal de traces, contadores ou do próprio HTTP 202.

`attempts.id` identifica uma tentativa HTTP. `canonicalId` identifica a mesma aceitação original nas três fontes, mesmo quando há retries ou aliases técnicos. Na integração com a aplicação, a normalização deve resolver a identidade original estável, incluindo o modo Business Fingerprint. Não usar trace OpenTelemetry ou uma nova chave gerada a cada replay como identidade canônica.

Uma tentativa que falhou sem receber identidade pode ter `canonicalId: null`. Se houve gravação apesar do timeout, a normalização precisa reconciliar a massa enviada com a Inbox para recuperar essa identidade. Sem essa evidência, o relatório não pode declarar cobertura completa.

Repetições idênticas do mesmo registro de evidência são deduplicadas. Dois registros diferentes com a mesma identidade na mesma fonte tornam a entrada inválida. As decisões são **snapshots de decisão atual**, não uma sequência de todas as tentativas: uma falha transitória anterior seguida de confirmação não deve ser fornecida como duas decisões conflitantes.

## Janela e corte de observação

- O manifesto declara `start`, `end` e `asOf`, em ISO-8601 com offset UTC `Z`.
- Tentativas elegíveis para a execução começam no intervalo `[start, end)`. `expectedAttempts` é a contagem independente de tentativas, incluindo as excluídas por contrato, sem samplers de token/setup.
- A coorte de negócio contém as identidades do experimento cujo **aceite original** ocorreu em `[start, end)`. Na captura local, o inventário deriva da massa tentada e reconciliada; não é inventário global de outros produtores concorrentes.
- `asOf` deve ser igual ou posterior a `end`; nenhuma evidência pode estar no futuro em relação a ele.
- Propostas ainda sem prazo vencido não entram no denominador de C; aparecem em `notYetDue`. Sem violações já observadas, o resultado fica `OBSERVING`, mesmo se todas já tiverem terminado rapidamente.
- No corte exato do vencimento, uma decisão com timestamp igual ao prazo é boa; sem decisão correta até ali, a proposta é ruim.

Esta versão avalia uma **coorte finita de experimento**, não uma janela móvel mensal. O futuro avaliador contínuo usará coortes por vencimento sem reaproveitar silenciosamente o filtro de aceite deste relatório.

## SLI A — aceite correto

```text
A = tentativas elegíveis com aceite correto / tentativas elegíveis
```

Uma tentativa boa possui status 202, `responseKind` igual a `RECEIVED` ou `DUPLICATE_ACCEPTED`, identidade reconciliada na Inbox e aceite ocorrido até o fim da resposta. Para `RECEIVED`, o aceite não pode anteceder o começo daquela tentativa. Um replay pode recuperar aceite anterior à janela; isso melhora A, sem criar outra proposta na coorte.

Um 202 sem registro conciliado não é bom. Timeout/erro de conexão (`status: 0`), 5xx e recusa técnica indevida são ruins. Uma gravação posterior ao timeout mantém aquela tentativa ruim, mesmo se a proposta for processada corretamente depois.

Meta candidata da política: `targets.acceptance`, explicitamente informada no manifesto, como `0.999` no exemplo. Não é inferida de um painel ou da taxa de resposta 202 isolada.

## Elegibilidade e exclusões

A elegibilidade é definida pela massa de teste e pelo contrato de autenticação/cota, **antes de olhar o resultado**. O normalizador fornece `eligible` e `exclusion`; o avaliador valida sua consistência.

| Categoria | `eligible` | `exclusion` | Regra |
|---|---|---|---|
| Requisição conforme contrato e dentro da cota/envelope | `true` | `none` | Falha técnica consome orçamento. |
| Payload deliberadamente inválido | `false` | `invalid_request` | Relatar separadamente; não é rejeição financeira. |
| Credencial deliberadamente inválida | `false` | `invalid_auth` | Relatar separadamente. Credencial válida rejeitada por falha do serviço não pertence aqui. |
| Cota comprovadamente excedida pelo cliente | `false` | `over_quota` | Não classificar todo 429 como exclusão. |

Uma tentativa elegível com 429 é ruim. Aceitar com 202 uma entrada declarada inelegível é uma inconsistência que impede aprovação; o relatório não comprova sozinho a correção das regras de validação da aplicação. Os samplers de token/setup não são tentativas de proposta.

## SLI B — aceite correto e rápido

```text
B_100ms = aceites corretos com duração <= 100 ms / tentativas elegíveis
B_200ms = aceites corretos com duração <= 200 ms / tentativas elegíveis
```

As metas desta versão são 95% e 99%, respectivamente. A duração é a observada no cliente, em milissegundos não negativos. Não usar somente o atributo `success` do JTL: uma assertion de duração pode falhar apesar do 202 correto, e A/B precisam conservar suas semânticas distintas. Erro rápido nunca é bom em B.

## SLI C — decisão correta até o prazo

Cada proposta única da coorte recebe prazo contado do aceite original:

```text
deadline = receivedAt + deadlineSeconds[classe]
C_classe = decisões corretas até deadline / propostas da classe com deadline <= asOf
```

Classes desta versão: `fast`, `standard` e `unrouted`. O manifesto exige prazo e alvo para as três; não há valor padrão de produção para não roteadas. O exemplo usa 300 s e 95% **somente como política explícita da fixture**.

`unrouted` é um controle adicional para propostas que não receberam classe de serviço. Não somar automaticamente seus denominadores com avaliações de uma proposta que depois foi roteada. Este relatório recebe uma classificação congelada por proposta; evolução de classe e compromissos sucessivos precisam do histórico persistido de SRE-04.

| Resultado normalizado | Decisão correta? | Conciliação |
|---|---|---|
| `confirmed` | Sim, se houver evidência válida do legado. | Confirmada. |
| `business_rejected` | Sim, se for rejeição comercial fundamentada. | Rejeitada. |
| `technical_failure` | Não. Não usar para converter indisponibilidade em rejeição comercial. | Aberta/não resolvida. |
| Decisão ausente | Não após o prazo; antes dele, em observação. | Aberta, inclusive se estiver na Inbox ou DLQ. |

Uma decisão correta tardia resolve o estado aberto, mas continua ruim para o prazo original. Um resultado anterior ao aceite ou sem aceite correspondente torna a medição incompleta. O estado `FAILED` atual do worker não deve ser traduzido automaticamente para rejeição comercial sem verificar sua causa.

As metas de fast 95%/120 s e standard 90%/2700 s são hipóteses para envelope viável, não garantias para três milhões de propostas novas com saída de 180/s.

## Conciliação e integridade

O relatório calcula, para a mesma coorte e base monetária:

```text
aceitas = confirmadas + rejeitadas de negócio + abertas
valor recebido = valor confirmado + valor rejeitado + valor aberto
```

Valores são inteiros em centavos, sem arredondamento de `double`. Overflow invalida a entrada. Cancelamentos e alterações de valor não são suportados nesta versão; o normalizador deve declarar cobertura incompleta se o cenário precisar desses estados.

Duplicatas de transporte não aumentam quantidade ou valor. Reconciliação de snapshots não comprova, por si só, ausência de dois efeitos financeiros no destino; essa evidência independente e persistência após reinício pertencem aos próximos incrementos. O arquivo não transforma o ledger em memória em registro durável.

## Cobertura e resultado

`coverage.client`, `coverage.inbox` e `coverage.decisions` declaram se as fontes estão completas até `asOf`, incluindo a certeza de que uma decisão ausente está realmente ausente. São atestações do produtor da evidência, não inferências do avaliador. A integração real ainda precisa produzi-las com checkpoints e inventário próprios.

Falta de fonte, tentativa faltante segundo `expectedAttempts`, aceite da coorte sem correlação com tentativa, registros conflitantes, campos obrigatórios ausentes e timestamps incoerentes impedem aprovação. O relatório não se autocertifica apenas porque os três flags estão `true`.

| Estado | Significado | Código de saída |
|---|---|---:|
| `PASS` | Eventos disponíveis, cobertura declarada completa, prazos da coorte vencidos e metas atendidas no conjunto fornecido. | 0 |
| `INCOMPLETE` | Fonte, schema, cobertura, cronologia ou saída inválida/indisponível. | 2 |
| `FAIL` | Há violação de meta na população já avaliada. Continuar mostrando prazos ainda em observação, quando houver. | 3 |
| `OBSERVING` | Nenhuma violação observada, mas restam prazos não vencidos. | 4 |
| `NO_EVENTS` | Não há tentativas elegíveis nem aceites novos da coorte. | 4 |

Sem denominador, `ratio` e `budgetConsumed` são `null`, não 100% ou zero artificial. Cada classe pode estar sem eventos mesmo quando outra classe possui resultado.

Orçamento em eventos: `eligible × (1 − target)`. Consumo: `bad / orçamento`. Não converter automaticamente para minutos nem usar esse orçamento para tolerar duplicação financeira.

## Evidência de referência

A fixture versionada contém três aceites de R$ 100 cada: um confirmado em 30 s, outro em 180 s e um sem decisão, todos na fast com prazo de 120 s. Após o vencimento:

- A = 3/3; B_100ms e B_200ms = 3/3.
- C_fast = 1/3 = 33,33%; dois eventos ruins: tardio e ainda aberto.
- Recebido = 30.000 centavos; confirmado = 20.000; aberto = 10.000.
- Resultado: `FAIL`, código 3. O código é esperado nesta demonstração.

## Obstáculos e pendências

- A captura local integra JTL/Inbox/worker no escopo documentado; um JTL sozinho continua sem fornecer decisão assíncrona ou cobertura da Inbox. Leitura em escala e múltiplas réplicas permanecem pendentes.
- Não há aprovação de prazos de produção, política de promoção, retenção ou operação mensal.
- Avaliações contínuas, eventos atrasados, múltiplas réplicas e comprovação de efeitos no legado exigem as etapas persistidas do [plano](plano-implementacao.md).
