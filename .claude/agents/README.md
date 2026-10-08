# Custom Claude Auditor Agents

Documentação dos agentes customizados de auditoria do projeto.

---

## 📋 Agentes Disponíveis

### 1. 🔐 LGPD Auditor
**Arquivo**: `lgpd-auditor.md`  
**Propósito**: Auditoria de conformidade com Lei 13.709/2018 (LGPD)

**O que verifica:**
- Exposição de PII (CPF, CNPJ, email, telefone, endereço, etc)
- Segurança de logs (não logar dados sensíveis)
- Sanitização de dados em traces e métricas
- Conformidade com artigos da LGPD
- Políticas de retenção de dados

**Quando usar:**
- Antes de fazer deploy de funcionalidade que trata dados pessoais
- Revisão de logging configuration
- Auditoria de traces/APM
- Validação de backups e acessos

**Severidade de Achados:**
- 🔴 **CRÍTICA**: PII exposado diretamente em produção
- 🟠 **ALTA**: Risco significativo de exposição
- 🟡 **MÉDIA**: Violação de boas práticas, risco controlado
- 🟢 **BAIXA**: Recomendação de melhoria

**Exemplo de Comando:**
```
@lgpd-auditor audita src/main/java/com/empresa/payment/ para exposição de PII
```

---

### 2. 💎 Code Quality Auditor
**Arquivo**: `code-quality-auditor.md`  
**Propósito**: Auditoria de qualidade de código (SOLID, Clean Code, Design Patterns)

**O que verifica:**
- Violações de SOLID principles
- Nomes descritivos e autoexplicativos
- Tamanho de métodos
- Uso de null vs Optional
- DRY (Don't Repeat Yourself)
- Comentários óbvios ou desatualizados
- Type safety e generics
- Design Patterns apropriados
- Testabilidade do código

**Quando usar:**
- Code review de mudanças significativas
- Refatoração de código legado
- Antes de fazer merge em main
- Validação de padrões do projeto

**Severidade de Achados:**
- 🔴 **CRÍTICA**: Múltiplas responsabilidades, impossível de testar
- 🟠 **ALTA**: Violação clara de SOLID ou Clean Code
- 🟡 **MÉDIA**: Recomendação de melhoria
- 🟢 **BOM**: Padrão bem aplicado

**Exemplo de Comando:**
```
@code-quality-auditor revisa src/main/java/com/empresa/payment/PagamentoService.java para SOLID
```

---

### 3. 🏗️ Architecture Auditor
**Arquivo**: `architecture-auditor.md`  
**Propósito**: Auditoria de arquitetura (Hexagonal, DDD, dependency rules)

**O que verifica:**
- Regra de dependência (domain → application → infrastructure)
- Estrutura de pacotes conforme Hexagonal
- Padrões DDD (Aggregate, Entity, Value Object, Domain Event, Repository)
- Ausência de circular dependencies
- Domain Services vs Application Services
- Bounded Contexts
- ArchUnit compliance

**Quando usar:**
- Estruturação de novo projeto
- Revisão antes de novo módulo
- Validação de layer boundaries
- Migração de código legado

**Severidade de Achados:**
- 🔴 **CRÍTICA**: Domain dependendo de Spring/JPA
- 🟠 **ALTA**: Application dependendo de Infrastructure
- 🟡 **MÉDIA**: Estrutura de pacotes não ideal
- 🟢 **BOM**: Padrão arquitetural bem aplicado

**Exemplo de Comando:**
```
@architecture-auditor valida regras de dependência e DDD patterns do projeto
```

---

## 🚀 Como Usar

### Opção 1: Usar via Menção (@mention)
```
@lgpd-auditor audita este repositório para conformidade LGPD
@code-quality-auditor verifica qualidade geral do código
@architecture-auditor valida estrutura hexagonal + DDD
```

### Opção 2: Usar via Skill CLI (futuro)
```bash
claude agent run lgpd-auditor "audita src/main/java"
claude agent run code-quality-auditor "revisa PagamentoService.java"
claude agent run architecture-auditor "valida dependências"
```

### Opção 3: Integrar em Workflow
```bash
# Validar antes de push
./scripts/audit.sh

# Rodar todos os agentes
claude agents run all
```

---

## 📊 Exemplo de Saída

### LGPD Auditor
```
🔐 AUDITORIA LGPD - Lei 13.709/2018
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 Escaneando: 23 arquivos Java, 4 configurações

🔴 CRÍTICA - PagamentoService.java:47
   CPF sendo logado sem sanitização
   └─ logger.info("CPF recebido: " + cpf);
   └─ Recomendação: Use LogSanitizer.sanitize(cpf)
   └─ Artigo LGPD: Art. 6º, III (Minimização)

✅ RELATÓRIO FINAL
   Status: ⚠️ Parcialmente Conforme
   Críticos: 1 | Altos: 2 | Médios: 0 | Baixos: 3
```

### Code Quality Auditor
```
💎 AUDITORIA DE QUALIDADE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 Analisando: PagamentoService.java (287 linhas)

🔴 CRÍTICA - Violação de Single Responsibility (Linha 45)
   └─ Método com 6 razões para mudar
   └─ Mixando validação + persistência + notificação + logging

✅ SCORE: 62/100 (Precisa melhorias)
   Estrutura: ⭐⭐⭐☆☆
   SOLID: ⭐⭐⭐☆☆
   Testabilidade: ⭐⭐⭐☆☆
```

### Architecture Auditor
```
🏗️ AUDITORIA DE ARQUITETURA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 Estrutura do projeto: Hexagonal + DDD

🔴 CRÍTICA - domain/model/Usuario.java:2
   Domain importando javax.persistence
   └─ ❌ import javax.persistence.Entity;
   └─ Viola regra: Domain não pode ter dependências

✅ CONFORMIDADE: 78/100
   Hexagonal: ⭐⭐⭐⭐☆
   DDD: ⭐⭐⭐⭐☆
   Dependency Rules: ⭐⭐⭐☆☆
```

---

## 🔄 Workflow de Auditoria Recomendado

### 1. Local (Before Commit)
```bash
# Verificar LGPD antes de fazer push de features que lidam com dados
@lgpd-auditor audita arquivo.java

# Validar qualidade do código
@code-quality-auditor revisa arquivo.java
```

### 2. PR (Before Merge)
```bash
# Triple check antes de fazer merge em main
@lgpd-auditor audita PR para conformidade
@code-quality-auditor valida qualidade geral
@architecture-auditor verifica mudanças arquiteturais
```

### 3. Periodicidade
```
- Semanal: Validar dados sensíveis em logs
- Mensal: Code quality review geral
- Trimestral: Arquitetura + DDD review completo
```

---

## 💡 Dicas de Uso

### Para LGPD Auditor
1. **Frequência**: Toda mudança que toca dados pessoais
2. **Escopo**: Começar com `application/` e `infrastructure/adapter/`
3. **Prioridade**: Achados CRÍTICOS devem ser corrigidos antes de merge
4. **Documentação**: Manter um registro de PII encontrado e mascarado

### Para Code Quality Auditor
1. **Frequência**: Todo código novo > 20 linhas
2. **Target**: Focar em `domain/service` e `application/usecase`
3. **Feedback**: Ler recomendações e refatorar proativamente
4. **Contínuo**: Usar como aprendizado para melhorar código futuro

### Para Architecture Auditor
1. **Frequência**: Antes de criar novos módulos/pacotes
2. **Scope**: Validar quando estrutura mudar
3. **ArchUnit**: Integrar tests em CI/CD
4. **Evolução**: Revisitar quando bounded contexts crescerem

---

## 🔗 Integração com Skills Existentes

Cada agente referencia as skills correspondentes:

- **lgpd-auditor** → `lgpd-sre-compliance-skill` + `spring-logging-skill`
- **code-quality-auditor** → `code-quality-guidance` + `testing-strategy-guidance`
- **architecture-auditor** → `architecture-guidance` + `service-modeling-skill`

---

## ⚙️ Configuração Técnica

### Tools Disponíveis
- **Glob**: Encontrar arquivos
- **Grep**: Buscar padrões regex
- **Read**: Ler conteúdo
- **Edit**: Sugerir correções

### Modelo
- Opus 5: Análise profunda e contextual

### Contexto
- Referências a CLAUDE.md e skills
- Padrões do projeto
- Convenções de nome

---

## 📞 Suporte

### Problema: Agente não encontra PII
**Solução**: Especificar arquivo ou diretório
```
@lgpd-auditor audita src/main/java/com/empresa/payment/
```

### Problema: Recomendação não faz sentido
**Solução**: Pedir contexto específico
```
@code-quality-auditor revisa PagamentoService.java linha 50-70 considerando logging
```

### Problema: Dúvida sobre recomendação
**Solução**: Consultar CLAUDE.md ou skill correspondente
```
Ver: .claude/CLAUDE.md - Regras invioláveis por tema
Ver: .claude/skills/lgpd-sre-compliance-skill/
```

---

## 📝 Notas Finais

- Agentes são **especializados** - use o correto para a tarefa
- Agentes são **complementares** - use em sequência para cobertura total
- Agentes são **educacionais** - aprendam com as recomendações
- Agentes são **práticos** - implementem código corrigido, não só críticas

**Combinação ideal**: LGPD + Code Quality + Architecture = Conformidade Total ✅

---

**Versão**: 1.0  
**Criado**: 2026-09-10  
**Padrão**: Claude Agent SDK
