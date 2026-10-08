# shared

Value objects e contratos trocados entre os serviços. **Sem Spring, sem AWS SDK e sem regra de negócio** — é a base que os outros módulos compartilham sem se acoplarem entre si.

## Por que existe

Ingestion, Dispatcher e Worker trocam mensagens e leem a mesma tabela. Sem um módulo comum, cada um reimplementaria a validação de CPF e o formato da mensagem, e as duas versões divergiriam. A ausência de Spring e de AWS SDK aqui é deliberada: mantém o módulo testável em milissegundos e impede que uma decisão de infraestrutura vaze para o contrato.

## O que contém

| Pacote | Conteúdo |
|---|---|
| `vo` | `Cpf` (valida dígitos; `toString` mascarado), `IdempotencyKey` (UUID estrito), `TenantId`, `Money` (BRL, escala 2, rejeita arredondamento silencioso) |
| `contract` | `ProposalMessage` + codec JSON, `Lane`, `ConvenioType`, `InboxAttributes` (nomes das colunas), headers HTTP |
| `payload` | `InboxPayload` + codec: os dados sensíveis que vão cifrados no inbox |
| `crypto` | `PayloadEnvelope` (AES-GCM + GZIP) e `DataKeyProvider` (chave local ou KMS) |

## Decisões que valem saber

**O CPF nunca aparece em texto claro por acidente.** `Cpf.toString()` devolve a forma mascarada, então um log ou uma mensagem de erro que interpole o objeto não vaza o número. O valor cru só sai por `digits()`, que é explícito.

**A mensagem de fila não carrega dado pessoal.** `ProposalMessage` leva apenas a chave de idempotência, o tenant, a lane e o score. O Worker lê o payload completo no DurableInbox pela chave. Assim o CPF existe em um lugar só, cifrado, e não se espalha pelas filas.

**Dinheiro é `BigDecimal` com escala 2.** `Money` rejeita valores com mais de duas casas em vez de arredondar, porque arredondamento silencioso de dinheiro é defeito, não conveniência.

## Test-jar

Publica um `test-jar` com os geradores jqwik (`DomainArbitraries`) usados nos testes de propriedade dos outros módulos. Para consumir:

```xml
<dependency>
  <groupId>br.com.consignado.platform</groupId>
  <artifactId>shared</artifactId>
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```

## Testes

18 classes de produção e 17 de teste, entre testes de exemplo e de propriedade. `SharedArchTest` garante por ArchUnit que o módulo não passe a depender de Spring nem da AWS.

```bash
mvn -pl shared test
```
