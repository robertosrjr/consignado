# Habilidade de Documentação Técnica de Software

Esta habilidade instrui o agente a produzir documentações técnicas de software (ADRs, DDAs, READMEs e Manuais Técnicos) seguindo padrões consolidados de engenharia de software e redação técnica.

---

## 1. Diretrizes de Escrita e Formatação

Para uma documentação focada e previsível, aplique as seguintes regras de formatação e estrutura:

1. **Foco na Clareza e Concisão**: A documentação deve servir como ferramenta de alinhamento para o time (*Whole Team Approach*). Cada seção deve ter um objetivo direto.
2. **Uso de Markdown Limpo**: Utilize cabeçalhos bem aninhados (`#`, `##`, `###`), blocos de código com a linguagem especificada (ex: ````python) e diagramas conceituais em texto utilizando sintaxe **Mermaid** sempre que for ilustrar fluxos.
3. **Ponto de Parada Natural (Saída Estruturada)**: o trabalho termina quando as seções obrigatórias do template escolhido estão preenchidas.

---

## 2. Templates de Documentos Obrigatórios

Ao redigir os documentos solicitados pelo usuário, utilize estritamente os seguintes formatos de template:

### A. Template de ADR (Architecture Decision Record)
Utilizado para documentar decisões de arquitetura significativas e o contexto por trás delas.

```markdown
# ADR-[Número]: [Título Curto e Autoexplicativo]

## Status
[Proposto | Aceito | Rejeitado | Superado por ADR-XX]

## Contexto
Descreva o cenário técnico e de negócio. Quais limitações, requisitos ou problemas de infraestrutura/código motivaram esta necessidade de mudança?

## Decisão
Descreva a solução técnica escolhida detalhadamente. Como ela resolve o problema? Por que foi escolhida em detrimento de outras opções?

## Consequências
*   **Positivas**: Quais ganhos o time terá com essa decisão (ex: melhor performance, facilidade de testes, desacoplamento)?
*   **Negativas/Trade-offs**: Quais novos custos técnicos, latências ou complexidades estamos assumindo?
```

### B. Template de DDA (Detailed Design Architecture / Documento de Design Detalhado)
Utilizado para detalhar a implementação técnica de um componente, serviço ou feature complexa antes de iniciar a codificação.

```markdown
# DDA: [Nome do Componente ou Funcionalidade]

## 1. Visão Geral e Objetivos de Negócio
Uma breve explicação do que este componente faz e qual o valor de negócio que ele entrega ao usuário final.

## 2. Arquitetura de Sistema e Fluxo de Dados
[Insira diagramas conceituais ou representações textuais (Mermaid) mostrando a integração dos componentes, endpoints ou serviços envolvidos]

## 3. Modelo de Dados e Interfaces de API
*   **Contratos de API (JSON payloads / Protocolos)**
*   **Esquema de Banco de Dados (Entidades, Atributos e Relacionamentos SQL/NoSQL)**

## 4. Estratégia de Qualidade e Testes (Alinhamento Ágil)
Indique como esta feature será testada conforme os Quadrantes do Teste Ágil:
*   **Q1/Q2 (Unidade e Componentes)**: Quais testes automatizados darão confiança aos desenvolvedores ao refatorar o código?
*   **Q3/Q4 (Integração e Performance)**: Há requisitos não-funcionais (tempo de resposta, concorrência, segurança) que precisam de validação explícita?
```

---

## 3. Formato de Saída Exigido

A entrega final segue esta estrutura:

1. **Sumário Executivo**: Resumo de 2-3 sentenças do que o documento aborda e quais fontes de código/requisitos o moldaram.
2. **O Documento Gerado**: O Markdown completo formatado de acordo com o template de ADR, DDA ou README especificado.
3. **Relatório de Obstáculos e Pendências (Obstacle Report)**:
    * Uma seção obrigatória no final listando:
        * Decisões pendentes (coisas que dependem de definições de negócio externos).
        * Riscos de integração identificados ou gargalos técnicos que o time principal deve monitorar.
        * Soluções alternativas ou caminhos alternativos que foram descartados no processo de design.
