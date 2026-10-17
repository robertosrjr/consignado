# Auditoria da documentação

Data: 16/10/2026. Revisão estática do repositório; aplicações e EC2 permaneceram desligadas. Não houve commit, publicação, apply de Terraform ou nova execução dos testes Java.

## Escopo e método

A varredura inicial abrangeu 89 arquivos Markdown encontrados pelo `rg`, respeitando arquivos ignorados e excluindo `target`, resultados de carga e `.local`. Foram conferidos destinos de links inline locais, hashes para duplicidade exata, documentos vazios e termos associados a versões, validação, infraestrutura, idempotência e tracing. A revisão semântica se concentrou nos READMEs, jornadas, guias operacionais e documentos de evidência, confrontados com código e configurações.

Isso não é uma validação integral de cada requisito dos SDDs, de cada comando de infraestrutura ou das fontes externas. Documentos normativos e versões históricas foram preservados e receberam contexto de escopo quando necessário.

## Achados e correções

| Prioridade | Achado | Tratamento |
|---|---|---|
| Alta | 26 links locais quebrados após reorganização das pastas | Corrigidos no README principal, jornadas, manual, pattern e plano de melhorias |
| Alta | Jornada técnica afirmava propagação de `traceparent` já implementada | Corrigida para `tracingId`; indicado que isso não restaura contexto de span no worker |
| Alta | Guia de tracing tratava erros hipotéticos como diagnóstico comprovado e usava endereço antigo | Reescrito com estado atual, fronteiras de contexto, protocolo e critérios de comprovação |
| Alta | Worker descrito como exactly-once entre quedas e com outbox completa | Explicitadas as limitações de estados/eventos em memória e dependência de contrato durável com o legado |
| Alta | README afirmava build verde com 356 testes como estado atual | Número histórico identificado como tal; registrado timeout da verificação ampla e sucesso de testes focados |
| Média | Badge de cobertura confundia meta de 80% com medição | Identificado como meta; removida a contagem estática de sucesso do badge de testes |
| Média | CloudWatch descrito como integração operacional comprovada | Separadas configuração versionada, emissão EMF em stdout e validação no ambiente ativo |
| Média | DynamoDB Local e ElasticMQ chamados indistintamente de emuladores oficiais | Identificados separadamente; DynamoDB Local usa memória neste iniciador |
| Média | `infrastructure/README.md` vazio | Criado guia da stack, variantes de Compose e situação do Terraform |
| Média | README ainda listava dashboards como pendentes | Atualizado para quatro dashboards publicados e sete regras SRE |
| Média | Regras de cores e precisão dos painéis não documentadas | Registradas comparações, arredondamento, duas casas monetárias e ausência de abreviações |
| Média | Runbook de alertas usava IP público antigo | Substituído por endereço a consultar após start da EC2 |
| Média | Comentário do iniciador local prometia arquivo `env.json` que não é gravado | Corrigido apenas o comentário; funcionamento do script preservado |
| Média | SDDs target e versões de orquestrador podiam ser confundidos com estado implementado | Acrescentadas notas de escopo e índice que separa aplicação Java, LaaS target e orquestrador |
| Baixa | Evidências do artigo pareciam um inventário permanente | Acrescentada nota sobre testes históricos, limpeza, desligamento e evoluções posteriores |

## Duplicidades e sobreposições

Não foram encontrados arquivos exatamente iguais por SHA-256 no inventário inicial. Isso não exclui duplicidade parcial ou conceitual.

- Jornada de negócio e jornada técnica têm públicos diferentes: ambas foram mantidas.
- Manual da API está em `narrativas/`, embora seja um guia operacional. O índice explicita essa localização; não houve nova movimentação de arquivos.
- Avaliações de métricas, base do artigo e README dos dashboards repetem conceitos com finalidades diferentes. O README dos dashboards passa a ser referência para consultas, filtros e cores atuais.
- Os SDDs LaaS target v1/v2/definitivo se sobrepõem. Os textos do orquestrador v3/v4 também registram versões de outro subsistema. Nenhuma versão foi apagada ou declarada superada sem decisão explícita.
- Auditoria de idempotência registra riscos do caminho padrão; o documento de Business Fingerprint descreve uma evolução opt-in. Não são implementações concorrentes a ativar simultaneamente.

## Fontes e limitações importantes

- `applications/worker/.../WorkerConfig.java` instancia `InMemoryProposalRepository`; o mock legado usa `AverbacaoLedger` em memória.
- `LocalDynamoDb.java` inicia com `-inMemory`. Não há durabilidade local entre reinícios.
- `SqsQueuePublisher` publica `tracingId`; o consumidor e o cliente HTTP do legado ainda não estabelecem tracing distribuído completo.
- O datasource CloudWatch existe nos arquivos, mas isso não comprova permissões ou dados no Grafana ativo.
- `deploy.yml` é um esqueleto que executa um gate e uma mensagem; não demonstra deployment automático da aplicação.
- A política de governança usa workflow externo versionado. Esta revisão não auditou sua implementação nem o resultado de suas execuções no GitHub.
- A árvore de trabalho já continha alterações, documentos movidos e exclusões antes da revisão. Esses arquivos não foram restaurados indiscriminadamente nem incluídos em commit.

## Validação reproduzível

Na raiz do repositório:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File applications/scripts/check-doc-links.ps1
git diff --check
```

O verificador cobre existência dos destinos locais de links Markdown inline. Não valida fragments/anchors, links de referência, links HTML, caminhos em blocos de código, renderização de Mermaid ou URLs externas. A comparação por hashes cobre duplicidade exata, não equivalência semântica.

Resultado final desta execução: 91 documentos Markdown, zero destinos locais ausentes e zero duplicados exatos. `git diff --check` passou. A análise sintática PowerShell do iniciador local também passou após a correção exclusiva de seu comentário.

## Obstáculos e pendências

1. Definir qual SDD target e quais ADRs orientam a próxima entrega; então marcar formalmente versões superadas.
2. Homologar carga e limites de capacidade com evidências reproduzíveis. Os 10.000 TPS continuam sendo meta.
3. Resolver/reavaliar a falha de integração concorrente antes de declarar toda a suíte verde; não inferir regressão corrigida de testes focados.
4. Implementar e validar tracing ponta a ponta e persistência/concorrência do worker.
5. Concluir validação e adoção dos recursos Terraform; não confundir arquivos existentes com infraestrutura provisionada.
6. Conferir números de mercado, afirmações regulatórias e fontes/datas antes de publicar narrativas. Esta revisão não certifica correção jurídica nem factual dessas referências.
7. `CLAUDE.md` referencia `.kiro/aws-aidlc-lite-rule-details/` ou `.amazonq/aws-aidlc-lite-rule-details/`, ausentes no checkout. Restaurar os recursos ou revisar esse processo com o responsável; não foram inventadas regras substitutas.
8. Converter o badge de testes em status real de uma suíte CI quando houver workflow específico validado. O badge atual é informativo.

Uma falha de substituição detectada durante a revisão foi corrigida antes da validação final; os READMEs e documentos movidos afetados foram recuperados mediante correspondência exata com o conteúdo original transformado, preservando a estrutura existente.
