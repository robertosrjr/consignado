# A Narrativa de Negócios: "O Paradoxo da Virada de Folha"

**Status:** Roteiro Executivo de Apresentação (Pitch C-Level)  
**Objetivo:** Defender o investimento na arquitetura de resiliência e ingestão massiva para Crédito Consignado  
**Público-Alvo:** VPs, Diretores de TI, Diretores de Operações e C-Level de Instituições Financeiras  

---

## Executive Summary

No mercado de crédito consignado brasileiro — uma oportunidade combinada que supera **R$ 484 bilhões** entre o segmento público/INSS e o consignado privado (Crédito do Trabalhador via eSocial/FGTS Digital) —, o momento da "virada de folha" representa a maior janela de originação do mês. 

Entretanto, as instituições financeiras enfrentam um paradoxo: **o evento mais lucrativo do mês é também o de maior risco operacional**. Quando a margem é liberada, picos repentinos de **10.000 TPS** atingem sistemas legados com capacidade máxima de **180 TPS**, provocando colapsos de infraestrutura, perda de participação de mercado para concorrentes digitais e graves riscos de sanção regulatória.

Este documento apresenta a narrativa de negócios estruturada em 4 atos para posicionar nossa PoC como um **produto de primeira linha (Enterprise-Grade)**.

---

## Ato 1: O Oportunismo de R$ 484 Bilhões e a Guerra de Milissegundos

### O Contexto de Mercado
O crédito consignado vive sua maior transformação regulatória e competitiva. Com a expansão do Crédito do Trabalhador e do consignado CLT via eSocial e FGTS Digital, a carteira privada ultrapassou **R$ 100 bilhões**, somando-se aos **R$ 384 bilhões** consolidados do INSS e setor público.

Neste mercado, a liderança é disputada palmo a palmo por gigantes tradicionais (**Banco do Brasil, Itaú, Caixa**) e instituições financeiras digitais altamente agressivas (**Parati CFI/MeuTudo, Facta, PicPay**).

### A Dinâmica Humana e Tecnológica
* **A Dona Maria / Trabalhador CLT:** O cliente simula o empréstimo antes da abertura da margem. Para ele, o processo deve ser transparente. Se o banco apresentar erro ou lentidão no primeiro minuto da virada, o correspondente bancário redireciona a proposta para a concorrência em questão de segundos.
* **O Correspondente Digital / "Pastinha":** Responsável pela originação em escala, o correspondente aciona robôs e scripts de automação que disparam a mesma proposta dezenas de vezes por segundo assim que o sistema do órgão público (Dataprev, eConsig, eSocial) abre às 00:01.

---

## Ato 2: A Paralisia do Core Banking e a Ameaça Regulatória

Quando a janela abre às 00:01, o volume do banco salta bruscamente de **50 TPS para 10.000 TPS**.

```
[ Pico de 10.000 TPS na Abertura ]
                │
                ▼
  🔥 Core Banking Legado Exaurido (Gargalo Físico: 180 TPS)
  🔥 Queda do Sistema por 40 a 60 Minutos
  🔥 Perda de Originação para Concorrentes Ativos
  🔥 Risco de Averbação Dupla por Race Conditions
  🔥 Riscos de Punição pela Autorregulação Febraban / ABBC
```

### O Impacto Financeiro e Operacional:
1. **Perda Imediata de Receita:** Durante o período de indisponibilidade (40 a 60 minutos), o banco deixa de capturar margens que são tomadas por concorrentes no primeiro minuto.
2. **Custo de Crise Operacional:** Equipes de infraestrutura e engenharia são mobilizadas nas madrugadas para reinicializações de emergência e limpeza de filas corrompidas.
3. **Risco de Sanções da Febraban:** A Autorregulação do Consignado (Febraban/ABBC) intensificou a fiscalização e já aplicou mais de **2.260 medidas administrativas** (suspensões e multas) contra bancos e correspondentes por práticas abusivas e falhas operacionais.
4. **Vulnerabilidade em Portabilidade:** Com o avanço do Open Finance (Resolução Conjunta CMN/BCB nº 15/2025), a ineficiência do banco na emissão do Demonstrativo Descritivo do Crédito (DDC) facilita a migração do seu cliente para a concorrência.

---

## Ato 3: A Virada Arquitetural — Borda Inteligente e Multi-Tenant

A solução não consiste em reescrever o Core Banking (projeto de dezenas de milhões de reais e anos de risco). A PoC prova que a resposta correta é a **Borda de Absorção Absoluta com Desacoplamento Temporal**:

### Como a Solução Atua na Prática:
1. **Absorção Imediata (Durable Inbox em Java 21):** A Ingestion API recebe as 10.000 req/s na nuvem (AWS ECS Fargate com Virtual Threads), registra a intenção no **Amazon DynamoDB** e retorna um protocolo `HTTP 202 Accepted` em p95 < 100 ms. O correspondente acalma os robôs ao receber a confirmação de recebimento.
2. **Filtro Anti-Spam Multi-Tenant & FinOps:** O WAF e o Gateway aplicam *rate limiting* por parceiro. Gravações condicionais no DynamoDB (`attribute_not_exists`) aceitam a 1ª proposta e rejeitam 499 duplicadas enviadas pelo mesmo robô no mesmo segundo.
3. **Orçamento de Capacidade e QoS (70/30):** O *AWS Lambda Dispatcher* analisa o perfil do cliente e do contrato via DynamoDB Streams, dividindo o tráfego em duas filas SQS:
   * **Fast Lane (70% do orçamento / 126 TPS):** Destinada a propostas de alta margem/LTV, produtos com seguros acoplados e solicitações de portabilidade via Open Finance.
   * **Standard Lane (30% do orçamento / 54 TPS):** Atende às demais operações com garantia de equidade (*fairness*).
4. **Alimentação Cadenciada do Legado:** Workers em ECS consomem as filas SQS e entregam ao Core Banking rigorosamente os **180 TPS seguros** através do *Resilience4j Rate Limiter*. O legado trabalha com 100% de eficiência e zero estresse.

---

## Ato 4: O Retorno sobre o Investimento (ROI Executivo)

Ao adotar esta arquitetura como produto de primeira linha, a instituição financeira alcança 4 ganhos estratégicos inquestionáveis:

* 🏆 **Proteção e Expansão de Market Share:** 100% de disponibilidade no momento crítico da virada, permitindo capturar as margens mais valiosas antes da concorrência.
* 💰 **Maximização do VGV (Volume Geral de Vendas) por QoS:** A priorização inteligente (70/30) garante que o capital disponível seja alocado nas operações mais rentáveis nos primeiros minutos.
* 🔒 **Risco Zero de Duplicidade e Conformidade Regulatória:** A verificação de idempotência na borda elimina averbações duplas no INSS/eSocial e cumpre integralmente as regras da Febraban, MTE e Banco Central.
* 📉 **Eficiência Operacional e FinOps:** Elimina o processamento inútil de requisições anômalas na nuvem e encerra os acionamentos de emergência das equipes de TI nas madrugadas.

---

## Matriz Comparativa: Modelo Tradicional vs. Produto de Primeira Linha

| Dimensão | Modelo Tradicional (Síncrono) | Produto PoC (Borda Desacoplada) |
| :--- | :--- | :--- |
| **Capacidade na Ingestão** | Limite rígido do legado (~180 TPS) | **10.000+ TPS sustentados na borda** |
| **Tempo de Resposta (SLA)** | > 5.000 ms (ou Timeout/503) | **HTTP 202 Accepted em p95 < 100 ms** |
| **Tratamento de Robôs/Spam** | Processa todas as requisições duplicadas | **Idempotência atômica: aceita 1, descarta 499** |
| **Ordem de Processamento** | Fila cega (FIFO / Quem chegar primeiro) | **QoS Inteligente (70% Fast Lane / 30% Standard)** |
| **Impacto no Core Banking** | DB Locks, gargalo físico e queda do sistema | **Fluxo conta-gotas estável travado em 180 TPS** |
| **Compliance & Febraban** | Alto risco de punição e averbação dupla | **Rastreabilidade total via Transactional Outbox** |
