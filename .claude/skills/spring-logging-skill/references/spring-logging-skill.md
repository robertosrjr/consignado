# Spring Boot 4.1.1: logs estruturados

Padrões e referências para instrumentar e revisar logs estruturados em aplicações Java 21 com Spring Boot 4.1.1.

## 1. Configuração de logs estruturados

A partir do Spring Boot 4.1.1, logs estruturados em JSON (ECS, GELF, Logstash) são nativos e ativados por propriedades:

```properties
# Elastic Common Schema (ECS) no console e em arquivo
logging.structured.format.console=ecs
logging.structured.format.file=ecs

# Metadados do serviço no formato ECS
logging.structured.ecs.service.name=meu-microsservico
logging.structured.ecs.service.version=1.0.0
logging.structured.ecs.service.environment=production
logging.structured.ecs.service.node-name=node-primary
```

Para Logstash JSON:
```properties
logging.structured.format.console=logstash
```

### Rotação de arquivos (Logback padrão do starter)
Sempre configure a rotação para evitar estouro de disco:
```properties
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.total-size-cap=2GB
logging.logback.rollingpolicy.max-history=7
```

---

## 2. API fluente do SLF4J (addKeyValue)

Os logs estruturados do Spring Boot incluem automaticamente os pares chave-valor do MDC e da API fluente do SLF4J. Assim as chaves são indexadas (Elastic/OpenSearch) sem poluir a mensagem:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TransacaoService {
    private static final Logger logger = LoggerFactory.getLogger(TransacaoService.class);

    public void processarTransacao(String transacaoId, String canal) {
        logger.atInfo()
              .addKeyValue("transacao_id", transacaoId)
              .addKeyValue("canal_origem", canal)
              .addKeyValue("status", "sucesso")
              .log("Transação processada");
    }
}
```

Campos de evento: identificadores técnicos, status e dimensões de baixa cardinalidade. Valores financeiros, dados pessoais e payloads ficam fora do log.

## 3. Dados pessoais (LGPD)

Não registre PII. Quando um identificador for indispensável ao diagnóstico, mascare na origem com o `LogSanitizer` e a tabela de formatos em `.claude/skills/lgpd-sre-compliance-skill/references/lgpd-sre-compliance-skill.md`. Essa é a única implementação do projeto; não copie o código para cá.

---

## 4. Checklist de revisão de código

Ao analisar Pull Requests ou código modificado:

1. **[REJEITAR]** `System.out.println()` ou `System.err.println()`.
2. **[REJEITAR]** Logger de framework específico (ex.: `java.util.logging`, Log4j direto) com o starter do Logback no classpath. Exija SLF4J (`org.slf4j.Logger`).
3. **[REJEITAR]** Concatenação de dados pessoais na mensagem, ex.: `logger.info("Usuario: " + cpf + " logado");`.
4. **[REJEITAR]** Valores financeiros, corpo de request/response, credenciais ou tokens no log.
5. **[APROVAR]** Propriedades `logging.structured.format.*` em `application.properties` ou `application.yml`.
6. **[APROVAR]** MDC ou `addKeyValue` para enriquecer eventos.
