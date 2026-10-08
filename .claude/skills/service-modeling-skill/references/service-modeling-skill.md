# Skill: Modelagem de Serviços e Ofertas na Arquitetura de Negócios (TAGF)

Este documento de habilidade instrui o agente sobre como mapear, documentar, analisar e diferenciar **Serviços de Negócio (Business Services)** e **Ofertas de Serviço (Service Offers)** com base no framework TAGF.

---

## 1. Fundamentação Conceitual (TAGF Core)

Na Arquitetura de Negócios da organização, Serviço de Negócio e Oferta de Serviço ajudam a modelar o fluxo e a entrega de valor, porém atuando em níveis diferentes de abstração e consumo:

### A. Serviço de Negócio (Business Service) — *O "Quê"*
* **Definição**: É uma capacidade de negócio exposta externamente (ou internamente para outras áreas), que entrega um resultado de valor bem definido, encapsulando os recursos necessários (processos, pessoas, tecnologia) para sua execução.
* **Características**:
  * **Estabilidade**: Possui alta estabilidade temporal (muda raramente).
  * **Independência**: É independente de tecnologia, canais de entrega ou modelos comerciais específicos.
  * **Foco**: Foca no resultado intrínseco de valor (ex: *Processar Pagamento*, *Conceder Crédito*, *Validar Identidade*).

### B. Oferta de Serviço (Service Offer) — *O "Como Consumir"*
* **Definição**: É a materialização, o empacotamento operacional e comercial de um ou mais Serviços de Negócio para que possam ser efetivamente consumidos por um público-alvo ou perfil de cliente específico.
* **Características**:
  * **Dinamismo**: É volátil e se adapta rapidamente às mudanças de mercado, campanhas e regulamentações.
  * **Parâmetros de Consumo**: Define regras claras de consumo, canais de acesso, níveis de acordo de serviço (SLA), limites de volume, termos de precificação (gratuito, assinatura, pay-per-use) e restrições regulatórias.
  * **Foco**: Foca na experiência de consumo e no modelo de entrega (ex: *Plano de Pagamento Pix Premium 24h com Isenção de Taxas*, *Oferta de Microcrédito Estudantil com Juros de 1.5%*).

---

## 2. Regras de Ouro para o Modelador
* **Evite o Microgerenciamento Técnico**: Antes de sugerir qual ferramenta, banco de dados ou tecnologia vai apoiar o serviço, entenda os objetivos de negócio e a estratégia. Se você não entende o valor intrínseco do Business Service para o cliente final, a modelagem está incorreta.
* **Relacionamento 1-para-N**: Um único Business Service (ex: *Entrega de Encomendas*) pode ser empacotado em múltiplas Service Offers distintas (ex: *Oferta Logística Expressa de 2 horas*, *Oferta Econômica de 5 dias úteis com rastreamento básico*). O serviço base é o mesmo; as ofertas variam nos parâmetros operacionais e comerciais.

---

## 3. Estrutura de Saída Exigida do Agente

Para que a resposta seja focada e previsível, o agente deve obrigatoriamente estruturar suas respostas em **quatro seções claras**:

### 1. Contexto Estratégico & Objetivo de Negócio
* Identifique brevemente o problema de negócio que está sendo resolvido e quem é o stakeholder/beneficiário final do valor.

### 2. Especificação do Serviço de Negócio (Business Service)
* **Nome**: (Verbo no infinitivo + substantivo, ex: *Mapear Risco*).
* **Descrição do Valor**: Qual a transformação de valor real gerada por este serviço?
* **Estabilidade**: Por que este serviço é estável no tempo?
* **Atores e Capacidades Internas**: Quem executa e quais capacidades de negócio dão suporte a ele?

### 3. Detalhamento da(s) Oferta(s) de Serviço (Service Offer)
* **Nome**: (Nome focado no público-alvo e na experiência, ex: *Pacote Gold de Análise Expressa de Crédito*).
* **Serviços de Negócio Vinculados**: Quais Business Services estão empacotados nesta oferta?
* **Canais de Disponibilização**: Onde a oferta é consumida (App, Portal, API, Loja Física)?
* **SLA & Parâmetros Operacionais**: Tempos de resposta, limites transacionais, restrições e garantias.
* **Modelo Comercial/Financeiro**: Como a organização monetiza ou gerencia o custo dessa oferta?

### 4. Relatório de Obstáculos e Riscos (Obstacle Report)
* Apresente os potenciais gargalos e riscos de governança ou sobreposição de conceitos identificados para este caso, incluindo soluções alternativas e boas práticas de arquitetura para mitigar falhas comuns na modelagem.
