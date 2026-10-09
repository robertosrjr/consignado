---
name: carga-caos-evidencia
description: "Use ao criar ou rodar testes de carga (k6) e de caos da PoC, ao medir vazão, latência, backlog ou duplicatas, ao dimensionar (Lei de Little, tempo de drenagem, capacidade do DynamoDB) e antes de alterar qualquer número ou badge do README. Para testes unitários e de integração use testing-strategy-guidance."
---

# Carga, caos e evidência

## Escopo

A PoC só vale o que consegue provar. Esta skill é a **extensão do projeto** para a skill genérica `chaos-engineering`: ela traz os cenários, as asserções de correção, as consultas e o lugar de publicação desta PoC. O **método** (hipótese, pré-requisitos, aprovação, linha de base, reset, teste negativo, relatório) vem da `chaos-engineering` e não é repetido aqui.

Os cenários e as consultas estão em `references/cenarios-e-consultas.md`.

**Nível de maturidade atual:** 1 (local, sem Docker). O nível 2 (CI com kind) chega com a M7.

**Asserções de correção da PoC** (estado estável de todo experimento, além das métricas de saída):
- zero averbação duplicada no juiz;
- zero proposta perdida (aceitas = concluídas + na fila + DLQ);
- `legacy_peak_window` ≤ 180.

## Fluxo de trabalho

1. **Escolha o cenário** da tabela de referência. Cada cenário prova uma invariante de `borda-absorcao-invariantes`.
2. **Dimensione antes de rodar:**
   - Lei de Little: `worker.max-in-flight ≥ 180 × latência p99 do legado (s)`;
   - tempo de drenagem: `propostas únicas ÷ 180`;
   - capacidade de escrita do DynamoDB: requisições/s × itens por requisição × fator de transação × tamanho em KB.

   Se o cenário não cabe no ambiente, reduza a escala e **declare a redução** na evidência.
3. **Descreva o ambiente** antes de medir: máquina, CPU, memória, número de instâncias de cada serviço, versões, commit e onde roda o gerador de carga.
4. **Aqueça** (1 a 2 min em carga baixa) e só então meça.
5. **Rode** cada cenário pelo menos 3 vezes e reporte a mediana e a pior execução.
6. **Cruze o lado do cliente com o lado do servidor.** O sumário do k6 sozinho não prova nada: confira com as métricas do servidor (consultas na referência).
7. **Publique** em `docs/evidencias/AAAA-MM-DD-<cenario>/`:
   - `ambiente.md`;
   - o sumário JSON do k6;
   - capturas dos painéis do Grafana;
   - as consultas usadas;
   - um parágrafo de conclusão com o que foi e o que **não** foi provado.
8. **Só então** atualize README e badges, sempre com link para a pasta da evidência.

## Regras

- **Número sem evidência é meta.** No README ele aparece como "meta" até existir pasta em `docs/evidencias/`.
- **Gerador e sistema na mesma máquina:** a evidência declara isso. Os números de destaque (10k req/s, p95) só valem com o gerador separado.
- **Latência é medida no servidor e no cliente.** Reporte p50, p95 e p99, nunca a média sozinha.
- **Duplicatas são medidas pelo juiz** (`mock-legacy`), não pela plataforma. Depois da M2, a contagem é por chave de negócio.
- **Caos sempre tem teste negativo** (só fora de produção, conforme a `chaos-engineering`). Desligue o mecanismo de proteção e mostre que o caos produz o defeito. Sem isso, não está provado que o mecanismo é necessário.
- **Reset entre execuções:** recrie a tabela `DurableInbox`, esvazie as filas e reinicie o `mock-legacy` (o ledger fica em memória). Sem isso, uma execução contamina a próxima.
- **Não há Docker na máquina de desenvolvimento.** Caos local é feito pelos parâmetros de caos do `mock-legacy` e matando processos (`scripts/local-up.ps1` e `local-down.ps1`). Toxiproxy e kind ficam para o CI (M7).
- **Sem PII nos scripts de carga.** Gere CPFs sintéticos válidos pelo dígito verificador e nunca use dados reais.
- **Não altere o teto (180) nem a QoS (70/30)** para fazer um teste passar. Se não passa, isso é o resultado.

## Delegação

- Método de experimento, checklist e modelo de relatório: `chaos-engineering`.
- O que cada cenário precisa provar: `borda-absorcao-invariantes`.
- Métricas novas e cardinalidade: `spring-metrics-skill`.
- Painéis, SLO e sinais: `sre-observability-skill`.
- Testes unitários e de integração: `testing-strategy-guidance`.
