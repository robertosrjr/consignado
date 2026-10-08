# platform-starter

Auto-configuração Spring Boot comum aos serviços. Quem depende deste módulo recebe logs estruturados, Actuator mínimo, sanitização de PII, tratamento de erro fechado e hardening HTTP **sem configurar nada**.

## Por que existe

Três serviços precisam das mesmas garantias de observabilidade e privacidade. Repetir isso em cada `application.yml` significa que um deles vai ficar diferente e ninguém vai notar até vazar um dado ou faltar uma métrica. Aqui as garantias são padrão, e o serviço só sobrescreve o que for específico dele.

## O que ele liga por padrão

| Área | Default |
|---|---|
| Logs | JSON ECS em stdout, com nome do serviço |
| LGPD | mascaramento de PII em todo valor de texto do JSON de log |
| Actuator | expõe apenas `health`, `info`, `metrics`, `prometheus`; sem detalhe de saúde |
| Métricas | tags `service` e `env`; política de cardinalidade da tag `tenant` |
| Erros | `ProblemDetail` genérico (RFC 7807), sem mensagem, stack trace ou nome de classe |
| Hardening | sem header `Server`, graceful shutdown, virtual threads ligadas |

Os defaults entram com a **menor precedência** (`addLast`), então qualquer variável de ambiente, argumento ou `application.yml` do serviço sobrescreve. Isso atende o Twelve-Factor: o mesmo artefato muda de comportamento por ambiente, sem recompilar.

## Sanitização de PII (LGPD)

Duas camadas, nesta ordem de importância:

1. **Minimização.** `PlatformProblemDetailAdvice` nunca registra a mensagem nem a stack trace da exceção — apenas o tipo, o tipo da causa raiz e a origem (`classe.metodo:linha`). A mensagem de uma exceção pode trazer um CPF ou um trecho de consulta com dado do cliente.
2. **Rede de proteção.** `PiiRedactingJsonMembersCustomizer` passa todo valor de texto do JSON pelo `LogSanitizer`, incluindo mensagem, campos do MDC, pares chave-valor e stack traces de bibliotecas que não controlamos.

```
Antes:  java.lang.IllegalStateException: SELECT * FROM proposal WHERE cpf = 52998224725
Depois: event="unexpected_error" exception="java.lang.IllegalStateException" origin="...:25"
        (e qualquer texto remanescente sai como ***.***.247-25)
```

`LogSanitizer` é a **implementação única** do projeto; a tabela de formatos vem de `.claude/skills/lgpd-sre-compliance-skill`. Não duplique esse código.

## Cardinalidade da tag `tenant`

`TenantTagPolicy` só dá tag própria aos tenants de uma allowlist; o resto agrega em `other`. Sem isso, um robô enviando `X-Tenant-ID` aleatório criaria uma série temporal nova por requisição e derrubaria o Prometheus.

```properties
platform.metrics.tenant-allowlist=banco-a,banco-b
platform.metrics.tenant-max=50
```

## Como usar

```xml
<dependency>
  <groupId>br.com.consignado.platform</groupId>
  <artifactId>platform-starter</artifactId>
</dependency>
```

## Testes

```bash
mvn -pl platform-starter test
```

34 testes, incluindo os casos da tabela de mascaramento, a prova de que o handler não registra a mensagem da exceção e a verificação de que a rede de proteção está registrada nos defaults.
