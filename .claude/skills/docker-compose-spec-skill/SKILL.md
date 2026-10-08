---
name: docker-compose-spec-skill
description: Habilidade especializada para projetar, validar e otimizar infraestruturas multicontêiner utilizando a Docker Compose Specification (compose.yaml). Use para criação de ambientes de dev/prod, otimização de builds, healthchecks e governança de containers.
allowed-tools: Read, Grep, Glob, Bash
model: sonnet
---

# Docker Compose Specification Skill

Esta habilidade orienta o projeto, validação e otimização de arquivos de orquestração de contêineres aderentes ao padrão **Compose Specification** (`compose.yaml`).

## Fluxo de Trabalho em 5 Etapas

1. **Mapeamento e Diagnóstico de Serviços**:
   - Identifique os serviços, dependências, portas expostas, variáveis de ambiente e persistência de dados.
   - Verifique a presença de arquivos `compose.yaml`, `compose.override.yaml` ou `.env`.

2. **Consulta à Referência Técnica**:
   - Para sintaxe detalhada de atributos (`build`, `deploy`, `healthcheck`, `networks`, `volumes`, `secrets`, `configs`), consulte `references/docker-compose-spec-reference.md`.

3. **Modelagem e Estruturação Aderente à Spec**:
   - Utilize a nomenclatura oficial de arquivo: `compose.yaml` (preferencial) ou `compose.override.yaml`.
   - Aplique o princípio de imutabilidade, interpolação estrita de variáveis (`${VAR:-default}`) e isolamento de redes.

4. **Validação Automática e Sintática**:
   - Execute a validação formal via CLI sem iniciar os contêineres:
     ```bash
     docker compose config --quiet
     ```
   - Verifique a interpolação final renderizada:
     ```bash
     docker compose config
     ```

5. **Aplicações de Padrões de Produção e Segurança**:
   - Garanta o uso de `secrets` e `configs` em vez de passar credenciais sensíveis em `environment`.
   - Configure `healthcheck` com testes adequados e `depends_on` com `condition: service_healthy`.
   - Adicione limites de CPU/memória em `deploy.resources.limits`.

## Regras e Diretrizes de Otimização
- **Evite o atributo legado `version`**: A especificação moderna do Compose Spec não exige o campo `version: '3.8'`.
- **Campos de Extensão (`x-`)**: Reutilize blocos YAML ancorados (`&anchor` e `*alias`) ou campos de extensão `x-` para evitar duplicação em múltiplos serviços.
- **Sobrescrita por Ambientes**: Mantenha as definições base em `compose.yaml` e variações em `compose.override.yaml` (para dev) ou `compose.prod.yaml` (para produção).
