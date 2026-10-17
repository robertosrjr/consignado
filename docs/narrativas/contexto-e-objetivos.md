# Contexto de negócio e objetivos da PoC

Conteúdo transferido do README principal em 16/10/2026. Os cenários e números abaixo registram a motivação e as metas do experimento; não são resultados de produção. Dados de mercado e referências regulatórias exigem fontes e data de consulta antes de publicação.

## Por que existe: o paradoxo da virada de folha

O crédito consignado é a linha de crédito pessoal de menor risco e maior rentabilidade do mercado brasileiro. A carteira combinada passa de **R$ 484 bilhões**: mais de R$ 384 bilhões em INSS e setor público, e mais de R$ 100 bilhões no consignado privado, via eSocial e FGTS Digital. A disputa entre bancos tradicionais e plataformas digitais se decide em milissegundos.

A cada **virada de folha** — quando o INSS, o eSocial ou os governos estaduais liberam a margem consignável — bilhões em margem ficam disponíveis de uma só vez, às 00:01. É a maior janela de originação do mês.

E aqui está o paradoxo: **o evento mais lucrativo do mês é também o de maior risco operacional.**

### Quem age no minuto zero

| Ator | Comportamento |
|---|---|
| **Cliente final** (aposentado, trabalhador CLT) | simula o empréstimo na véspera e não tolera falha; se o sistema travar, o correspondente leva a proposta para o concorrente |
| **Correspondente bancário e fintechs parceiras** | não operam manualmente: usam robôs que disparam a **mesma proposta centenas de vezes por segundo**, no milissegundo em que a janela abre, para garantir a margem antes dos rivais |

O resultado é um salto de **50 requisições por segundo para 10.000**, de forma instantânea, contra um Core Banking projetado para **180**.

### O que acontece hoje, com arquitetura síncrona

- O pool de conexões do banco de dados se esgota em **menos de 3 segundos**.
- O sistema entra em timeout em cascata e cai, ficando fora do ar de **40 a 60 minutos**.
- A liderança de TI é convocada de madrugada para reiniciar servidores e limpar filas.
- Nesse intervalo, concorrentes resilientes absorvem a demanda. A perda de originação é irrecuperável, porque a margem do cliente já foi tomada.
- No caos dos retries dos robôs, requisições duplicadas criam condições de corrida e geram **averbação duplicada** no INSS ou no eSocial — litígio judicial e exposição às sanções da Autorregulação do Consignado (Febraban e ABBC), que já acumula mais de **2.260 medidas administrativas**.

Ineficiência na portabilidade agrava o quadro: com o Open Finance e a Resolução Conjunta CMN/BCB nº 15/2025, a demora do banco facilita a migração do próprio cliente para a concorrência.

## Objetivo

Provar, com código funcionando e métricas, que **não é preciso reescrever o Core Banking** para sobreviver à virada. A alternativa — expandir o legado para 10.000 TPS síncronos — custaria dezenas de milhões e anos de risco.

A PoC precisa demonstrar quatro coisas:

| Pilar | O que precisa ficar provado |
|---|---|
| **Absorção imediata** | a borda sustenta 10.000 req/s e responde `HTTP 202` com p95 abaixo de 100 ms, acalmando os robôs com um protocolo oficial |
| **Idempotência radical** | 500 disparos da mesma proposta no mesmo segundo gravam **um** registro e descartam os outros 499, sem custo e sem risco de averbação dupla |
| **Proteção física do legado** | o Core Banking recebe um fluxo estável travado em 180 TPS, trabalhando com eficiência e sem sobressalto |
| **Garantia de margem por QoS** | a capacidade escassa vai primeiro às operações mais rentáveis: alto valor, seguros acoplados e portabilidade |

### Ganhos que justificam o investimento

Disponibilidade no momento crítico protege e expande participação de mercado. A priorização 70/30 maximiza o VGV nos primeiros minutos, quando a margem mais valiosa está disponível. A idempotência na borda elimina o risco de duplicidade e atende Febraban, MTE e Banco Central. E o descarte das duplicatas na borda corta processamento inútil na nuvem, encerrando também os acionamentos de emergência de madrugada.

## Como a PoC é homologada

O teste de carga precisa evidenciar, ao vivo:

- **Absorção na borda**: 10.000 TPS sustentados, mais de 99,9% de `202`, p95 abaixo de 100 ms.
- **Linha de proteção do legado**: vazão desenhando uma reta travada em 180 TPS. No legado simulado, `legacy_peak_window` deve ficar em 180 ou abaixo, e nenhum `503` por capacidade deve aparecer.
- **Integridade da idempotência**: injeção de requisições concorrentes com chaves duplicadas produzindo exatamente um registro por chave. `legacy_averbacoes_total{kind="duplicate"}` deve ser zero.
- **Effective drain time**: o tempo estimado para esvaziar o backlog acumulado após o pico, em tempo real.

Avaliação das métricas de negócio e de aplicação, com as consultas prontas: [docs/brainstorming/](../brainstorming/).

## Leituras relacionadas

- [Narrativas de origem](./).
- [Visão técnica](../manuais/visao-tecnica.md).
- [Estado atual e limitações](../estado-atual.md).
