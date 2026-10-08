# Guia de Referência Técnica: Docker Compose Specification

Este guia reúne os componentes, sintaxes, schemas e padrões de produção oficiais da **Compose Specification** (`https://github.com/compose-spec/compose-spec/blob/main/spec.md`).

---

## 1. Estrutura Fundamental do Arquivo

A especificação Compose unificou o formato de arquivo, tornando a tag `version` opcional/obsoleta. O arquivo raiz é composto pelas seguintes seções de nível superior:

```yaml
name: meu-projeto # Nome do projeto (opcional, padrão: nome da pasta)

services: # Definição dos contêineres e serviços
  app: ...

networks: # Definição das redes virtuais isoladas
  frontend: ...

volumes: # Definição dos volumes de persistência
  db-data: ...

secrets: # Arquivos e dados sensíveis
  db-password: ...

configs: # Arquivos de configuração não sensíveis
  nginx-conf: ...
```

---

## 2. Atributos de Serviços (`services`)

### 2.1. Construção de Imagens (`build`)
A seção `build` aceita tanto uma string simples com o caminho quanto um objeto completo:

```yaml
services:
  web:
    build:
      context: ./app
      dockerfile: Dockerfile.prod
      target: production
      args:
        BUILD_VERSION: "1.2.0"
      cache_from:
        - myregistry/web:cache
      secrets:
        - npmrc
```

### 2.2. Gerenciamento de Dependências (`depends_on`)
Garante a ordem de inicialização e verifica o estado de integridade do serviço dependente:

```yaml
services:
  web:
    image: myapp:latest
    depends_on:
      db:
        condition: service_healthy
        restart: true
      redis:
        condition: service_started
```

### 2.3. Checagem de Saúde (`healthcheck`)
Monitora a saúde interna do contêiner para restart automático ou controle de dependência:

```yaml
services:
  db:
    image: postgres:16-alpine
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
```

### 2.4. Variáveis de Ambiente e Arquivos `.env` (`environment` e `env_file`)
```yaml
services:
  api:
    image: api:v1
    env_file:
      - .env.common
      - path: .env.secret
        required: false
    environment:
      PORT: "8080"
      NODE_ENV: ${NODE_ENV:-development}
```

### 2.5. Portas e Redes (`ports` e `networks`)
```yaml
services:
  web:
    image: nginx:alpine
    ports:
      - target: 80
        published: 8080
        protocol: tcp
        mode: host
    networks:
      - frontend
      - backend

networks:
  frontend:
    driver: bridge
  backend:
    internal: true # Impede acesso externo à rede
```

### 2.6. Volumes e Persistência (`volumes`)
```yaml
services:
  db:
    image: postgres:16
    volumes:
      - type: volume
        source: db-data
        target: /var/lib/postgresql/data
      - type: bind
        source: ./init.sql
        target: /docker-entrypoint-initdb.d/init.sql
        read_only: true

volumes:
  db-data:
    driver: local
```

### 2.7. Segredos e Configurações (`secrets` e `configs`)
```yaml
services:
  app:
    image: myapp:latest
    secrets:
      - source: db_secret
        target: /run/secrets/db_password
        uid: "1000"
        gid: "1000"
        mode: 0400
    configs:
      - source: app_config
        target: /etc/app/config.json

secrets:
  db_secret:
    file: ./secrets/db_password.txt

configs:
  app_config:
    file: ./config/app.json
```

---

## 3. Recursos e Implantação (`deploy`)

A seção `deploy` define comportamento de escala, limites de recursos e estratégias de atualização:

```yaml
services:
  worker:
    image: worker:v2
    deploy:
      mode: replicated
      replicas: 3
      resources:
        limits:
          cpus: '0.50'
          memory: 512M
        reservations:
          cpus: '0.25'
          memory: 256M
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
        window: 120s
```

---

## 4. Reutilização de Código: Ancoragem YAML e Extensões (`x-`)

Para manter os arquivos DRY (Don't Repeat Yourself), a especificação permite utilizar blocos de extensão com prefixo `x-`:

```yaml
x-logging-config: &default-logging
  logging:
    driver: json-file
    options:
      max-size: "10m"
      max-file: "3"

x-common-env: &common-env
  LOG_LEVEL: debug
  REGION: us-east-1

services:
  web:
    image: web:latest
    <<: *default-logging
    environment:
      <<: *common-env
      SERVICE_NAME: web

  api:
    image: api:latest
    <<: *default-logging
    environment:
      <<: *common-env
      SERVICE_NAME: api
```

---

## 5. Matriz de Comandos da CLI (`docker compose`)

| Comando | Descrição |
| :--- | :--- |
| `docker compose config` | Valida a sintaxe e exibe o YAML estendido e interpolado. |
| `docker compose up -d` | Compila, cria e inicia os contêineres em segundo plano. |
| `docker compose down -v` | Paralisa e remove contêineres, redes e volumes nomeados. |
| `docker compose ps` | Lista os contêineres e seus respectivos status de `healthcheck`. |
| `docker compose logs -f <service>` | Acompanha os logs em tempo real de um serviço específico. |
| `docker compose exec <service> <cmd>` | Executa um comando dentro do contêiner em execução. |
