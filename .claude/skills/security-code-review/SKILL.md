---
name: security-code-review
description: "Use when reviewing Java/Spring code changes for application security: hardcoded secrets, SQL/JPQL/command injection, unsafe deserialization, weak cryptography or TLS settings, and sensitive details leaked in API error responses. For hexagonal architecture rules use architecture-guidance; for PII in telemetry use lgpd-sre-compliance-skill; for prompt injection use saif-skill."
---

# Skill: Security Code Review

## Objetivo
Analisar alterações de código Java/Spring em busca de vulnerabilidades de segurança de aplicação e exposição de credenciais.

## Regras de Avaliação

### 1. Segredos Hardcoded (CRITICAL)
- **Regra**: Nenhuma chave de API, token JWT, senha ou credencial de banco pode estar estática no código ou em configuração versionada.
- **Detecção**: Atribuições como `API_KEY = "..."`, `SECRET = "..."`, `password = "..."`, `spring.datasource.password=` com valor literal.
- **Ação**: Marcar como `CRITICAL` e exigir variável de ambiente (`System.getenv()`, `${VAR}` no `application.yml`) ou cofre de segredos (Vault, Secrets Manager).

### 2. Injeção (CRITICAL)
- **Regra**: Nenhuma consulta SQL/JPQL ou comando de sistema montado por concatenação com dado externo.
- **Detecção**: `"SELECT ... " + param`, `createQuery("..." + param)`, `Runtime.exec`/`ProcessBuilder` com entrada do usuário.
- **Ação**: Exigir parâmetros ligados (`?`, `:param`, `@Query` com parâmetros, Criteria API) e lista de valores permitidos para comandos.

### 3. Desserialização Insegura (CRITICAL)
- **Regra**: Não desserializar dados não confiáveis com `ObjectInputStream` nem habilitar tipagem polimórfica irrestrita no Jackson (`enableDefaultTyping`, `@JsonTypeInfo(use = Id.CLASS)`).
- **Ação**: Usar DTOs explícitos e, se houver polimorfismo, lista de subtipos permitidos.

### 4. Criptografia e Transporte (MAJOR)
- **Regra**: Nada de algoritmos fracos (`MD5`, `SHA-1` para senhas, `DES`, `ECB`) nem desativação de validação TLS (`TrustAll`, `HostnameVerifier` que aceita tudo).
- **Ação**: Senhas com `BCryptPasswordEncoder`/Argon2; TLS com validação padrão.

### 5. Vazamento em Respostas de Erro (MAJOR)
- **Regra**: Respostas de API não expõem stack trace, SQL, nomes de classes internas ou segredos.
- **Ação**: Tratar exceções com `ProblemDetail` genérico (ver `api-design-guidance`) e registrar o detalhe só no log, sem PII.

## Fora do escopo desta skill
- Regra de dependência da Arquitetura Hexagonal → `architecture-guidance`.
- Injeção de dependência pelo construtor e demais regras de Clean Code → `code-quality-guidance`.
- Dados pessoais em logs, traces e métricas → `lgpd-sre-compliance-skill`.
- Prompt injection e segurança de pipelines de IA → `saif-skill`.
