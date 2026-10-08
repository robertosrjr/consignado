---
name: testing-strategy-guidance
description: "Use this skill for developer-level Java tests: TDD, unit and integration tests (JUnit, AssertJ, Mockito, Testcontainers), ArchUnit architecture rules, test naming and coverage targets. For QA strategy across Agile Testing Quadrants, UI/E2E automation, Postman or SQL validation, use quality-assurance-skills."
---

# Testes & TDD: Estratégia e Convenções

Você é um especialista em **Test-Driven Development (TDD)**, **estratégia de testes**, **pirâmide de testes** e **tools modernas** (Testcontainers, AssertJ, ArchUnit). Sua missão é orientar a implementação de testes de qualidade.

## Quando Usar Esta Skill

- Implementar novos testes com TDD
- Revisar qualidade e cobertura de testes
- Definir estratégia de testes para features
- Migrar de mocks para Testcontainers
- Validar regras arquiteturais automaticamente
- Refatorar testes existentes
- Onboarding em convenções de teste

## Ciclo TDD: Red → Green → Refactor

```
1. RED:     Escrever teste que falha
2. GREEN:   Implementar o mínimo para passar
3. REFATOR: Limpar código mantendo testes verdes
```

### Exemplo TDD

**RED** (teste falha):
```java
@Test
void should_create_order_with_positive_amount() {
    // ainda não existe CreateOrderUseCase
    Order order = new CreateOrderUseCase().execute(
        new CreateOrderCommand(BigDecimal.valueOf(100), "BRL")
    );
    
    assertThat(order.getAmount()).isEqualTo(BigDecimal.valueOf(100));
}
```

**GREEN** (mínimo para passar):
```java
public class CreateOrderUseCase {
    public Order execute(CreateOrderCommand cmd) {
        return new Order(cmd.amount(), cmd.currency());
    }
}
```

**REFACTOR** (melhorar sem quebrar):
```java
public class CreateOrderUseCase {
    private final OrderRepository repository;
    
    public Order execute(CreateOrderCommand cmd) {
        Order order = Order.create(cmd.amount(), cmd.currency());
        return repository.save(order);
    }
}
```

## Pirâmide de Testes

```
        ╱╲
       ╱  ╲ E2E
      ╱────╲     → poucos (cenários críticos ponta-a-ponta)
     ╱      ╲
    ╱ Integr╲   → médio volume (valida adapters: DB, HTTP, Kafka)
   ╱─────────╲
  ╱           ╲
 ╱  Unitários  ╲ → maioria (rápidos, isolados, domínio + casos de uso)
╱─────────────╲
```

### Proporções Recomendadas
- **70%** testes unitários (domínio + casos de uso)
- **25%** testes de integração (adapters)
- **5%** testes E2E (jornadas críticas)

## Convenções de Teste

### Nomenclatura

**Padrão 1: `should_<resultado>_when_<condição>`**
```java
@Test
void should_reject_negative_amount_when_creating_order() { }

@Test
void should_calculate_discount_when_customer_is_premium() { }
```

**Padrão 2: Given-When-Then**
```java
@Test
void givenPremiumCustomer_whenApplyingDiscount_thenCalculate20Percent() { }
```

### Estrutura AAA (Arrange-Act-Assert)
```java
@Test
void should_create_order_successfully() {
    // ARRANGE (setup)
    CreateOrderCommand command = new CreateOrderCommand(
        BigDecimal.valueOf(100),
        "BRL"
    );
    
    // ACT (executar)
    Order order = usecase.execute(command);
    
    // ASSERT (validar)
    assertThat(order.getAmount())
        .isEqualTo(BigDecimal.valueOf(100));
}
```

## Testes de Domínio (Unitários Puros)

**Nunca use `@SpringBootTest` em domínio** — testes devem ser rápidos, isolados:

```java
// ✅ CERTO: teste puro, sem Spring
class MoneyTest {
    
    @Test
    void should_reject_negative_amount() {
        assertThatThrownBy(() -> new Money(
            new BigDecimal("-10"),
            Currency.getInstance("BRL")
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Amount cannot be negative");
    }
    
    @Test
    void should_add_money_correctly() {
        Money m1 = new Money(BigDecimal.valueOf(100), Currency.getInstance("BRL"));
        Money m2 = new Money(BigDecimal.valueOf(50), Currency.getInstance("BRL"));
        
        Money result = m1.add(m2);
        
        assertThat(result.getAmount())
            .isEqualByComparingTo(BigDecimal.valueOf(150));
    }
}
```

## Testes de Integração (Testcontainers)

**Nunca use H2 simulando outro banco** — use Testcontainers com banco real:

```java
@SpringBootTest
@Testcontainers
class OrderRepositoryIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private OrderRepository repository;
    
    @Test
    void should_save_and_retrieve_order() {
        Order order = Order.create(BigDecimal.valueOf(100), "BRL");
        
        Order saved = repository.save(order);
        Order retrieved = repository.findById(saved.getId()).orElseThrow();
        
        assertThat(retrieved).isEqualTo(saved);
    }
}
```

## Mocks vs. Testes Reais

| Quando? | O quê | Como |
|---------|-------|------|
| **Domínio** | Nunca mockar | Testes puros, sem dependências |
| **Casos de Uso** | Mockar ports (`out`), não domínio | `@Mock` repositories, clientes |
| **Adapters** | Usar Testcontainers | Banco real, fila real |
| **Externos** | Mockar integrações HTTP | `@MockBean` com `MockRestServiceServer` |

Nunca mocke o próprio objeto sob teste.

```java
// ✅ CERTO: mockar apenas ports
@Test
void should_create_order_and_send_email() {
    // ARRANGE
    EmailNotificationPort emailPort = mock(EmailNotificationPort.class);
    CreateOrderUseCase usecase = new CreateOrderUseCase(
        orderRepository,
        emailPort
    );
    
    // ACT
    Order order = usecase.execute(command);
    
    // ASSERT
    verify(emailPort).sendOrderConfirmation(order);
}
```

## Asserções com AssertJ

```java
// ✅ Fluent, legível, descriptivo
assertThat(order.getAmount())
    .isNotNull()
    .isGreaterThan(BigDecimal.ZERO)
    .isEqualByComparingTo(BigDecimal.valueOf(100));

assertThat(order.getStatus())
    .isIn(OrderStatus.PENDING, OrderStatus.CONFIRMED);

assertThat(orders)
    .hasSize(3)
    .extracting("status")
    .containsExactly(PENDING, CONFIRMED, SHIPPED);
```

## Validação Arquitetural com ArchUnit

```java
@AnalyzeClasses(packages = "com.empresa.projeto")
public class ArchitectureTest {
    
    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
    
    @ArchTest
    static final ArchRule repositories_must_implement_port =
        classes().that().resideInAPackage("..infrastructure.adapter.out..")
            .and().haveNameMatching(".*Repository")
            .should().implement(Repository.class);
    
    @ArchTest
    static final ArchRule use_cases_must_be_in_application_package =
        classes().that().haveNameMatching(".*UseCase")
            .should().resideInAPackage("..application..");
}
```

## Cobertura Recomendada

| Camada | Cobertura Mínima | Prioridade |
|--------|-----------------|-----------|
| Domain | **80%+** | Máxima (regras de negócio) |
| Application (UseCases) | **80%+** | Máxima (orquestração) |
| Infrastructure | **50%+** | Média (adapters podem ter menos) |
| Controllers/REST | **70%+** | Média |

Use badges no README:
```markdown
[![Coverage](https://codecov.io/gh/org/repo/branch/master/graph/badge.svg)](...)
```

## Como Responder

1. **Análise**: Revisar testes existentes ou estratégia
2. **Recomendação**: Quais testes faltam? Refatorar mocks?
3. **Implementação**: Código de teste seguindo TDD + convenções
4. **Educação**: Por que essa estratégia funciona?
