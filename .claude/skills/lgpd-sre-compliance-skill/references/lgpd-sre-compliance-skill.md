# LGPD-SRE Compliance: auditoria e mascaramento de dados pessoais

Procedimentos, alinhamento regulatório (Lei 13.709/2018) e o **modelo único de mascaramento** do projeto para identificar, mascarar e auditar exposição de dados pessoais (PII) em logs, traces, métricas e ambientes SRE.

> Esta é a fonte única de mascaramento de PII. `CLAUDE.md`, `spring-logging-skill` e os agentes apontam para cá; não duplique o código em outros arquivos.

## 1. Alinhamento regulatório

- **Art. 6º, III (Minimização):** a telemetria ingere e retém apenas o mínimo de metadados necessário à confiabilidade. Nenhum payload de usuário nos índices de telemetria.
- **Art. 6º, VII (Segurança):** logs, spans e backups operacionais são estruturados e protegidos contra vazamento.
- **Art. 46 (Medidas de segurança e prevenção):** o ambiente operacional impede acesso a PII bruto durante resposta a incidentes e postmortems.

## 2. Workflow de auditoria

### Passo 1: detectar PII e exposição
- Procure variáveis ou chaves como `cpf`, `email`, `phone`, `telefone`, `password`, `card`, `token`, `address`, `endereco`, `ip_address`, `username`.
- Localize chamadas de log, atributos de span e tags de métrica que registram esses valores diretamente (ex.: `logger.info("Usuario: " + cpf)`).

### Passo 2: avaliar a sanitização
- Verifique se existe camada de sanitização na origem (utilitário, interceptor, filtro de log).
- Sem ela, registre uma lacuna de conformidade de severidade alta.

### Passo 3: mascarar na origem
- Minimize primeiro: se o dado não tem valor diagnóstico, não o registre.
- Quando precisar manter uma forma derivada, aplique a tabela da seção 3 antes de escrever o log.

### Passo 4: auditar o acesso operacional
- Elimine leitura manual de logs de produção via SSH.
- Use coleta central e imutável, com RBAC e acesso *break-glass* auditado.

## 3. Tabela de mascaramento obrigatório

| Dado | Formato mascarado | Exemplo |
|---|---|---|
| CPF | Apenas o último grupo e os dígitos verificadores | `123.456.789-10` → `***.***.789-10` |
| E-mail | Primeira e última letra do usuário; domínio preservado | `joana.silva@empresa.com.br` → `j***a@empresa.com.br` |
| Cartão | Apenas os 4 primeiros e os 4 últimos dígitos | `4111 1111 1111 1111` → `4111-XXXX-XXXX-1111` |
| Telefone | Apenas os 4 últimos dígitos | `(11) 98765-4321` → `(**) *****-4321` |
| Senha, token, chave de API | Nunca registrar, nem mascarado | — |

CPF sem pontuação (11 dígitos seguidos) também é mascarado. Um telefone de 11 dígitos sem pontuação cai na regra de CPF, o que continua ocultando o número.

## 4. Implementação de referência (Java)

Testado com os exemplos da tabela acima. Nunca devolve `null`.

```java
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogSanitizer {

    private static final Pattern CARD =
        Pattern.compile("\\b(\\d{4})[- ]?\\d{4}[- ]?\\d{4}[- ]?(\\d{4})\\b");
    private static final Pattern CPF =
        Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?(\\d{3})-?(\\d{2})\\b");
    private static final Pattern PHONE =
        Pattern.compile("\\(\\d{2}\\)\\s?\\d{4,5}-(\\d{4})");
    private static final Pattern EMAIL = Pattern.compile(
        "\\b([A-Za-z0-9])(?:[A-Za-z0-9._%+-]*([A-Za-z0-9]))?(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b");

    private LogSanitizer() {
    }

    public static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        String masked = CARD.matcher(message).replaceAll("$1-XXXX-XXXX-$2");
        masked = CPF.matcher(masked).replaceAll("***.***.$1-$2");
        masked = PHONE.matcher(masked).replaceAll("(**) *****-$1");
        return EMAIL.matcher(masked).replaceAll(LogSanitizer::maskEmail);
    }

    private static String maskEmail(MatchResult m) {
        String masked = m.group(1) + "***" + Objects.toString(m.group(2), "") + m.group(3);
        return Matcher.quoteReplacement(masked);
    }
}
```

Cuidados ao alterar:
- A ordem importa: cartão antes de CPF, para que 16 dígitos não sejam lidos como CPF.
- Toda referência `$n` na substituição precisa de um grupo de captura correspondente no regex; senão, `replaceAll` lança `IndexOutOfBoundsException` em tempo de execução.
- Use o sanitizador como rede de proteção. A regra principal continua sendo não passar PII ao logger.

```java
// ✅ Evento sem PII; se um identificador for indispensável, mascare na origem
logger.atInfo()
    .addKeyValue("pedido_id", pedidoId)
    .addKeyValue("cliente_cpf", LogSanitizer.sanitize(cpf))
    .log("Pedido criado");

// ❌ PII concatenado na mensagem
logger.info("Pedido criado para cpf=" + cpf + " email=" + email);
```
