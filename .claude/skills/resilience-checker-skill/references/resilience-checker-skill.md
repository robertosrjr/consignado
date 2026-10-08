Quando o usuário solicitar uma análise de resiliência do código, siga as etapas abaixo estruturadamente:

1. **Varredura e Mapeamento de Dependências:**
   - Identifique todas as chamadas a serviços externos, bancos de dados, APIs de terceiros ou recursos compartilhados. Estes são os pontos críticos de potencial falha.

2. **Avaliação e Aplicação de Padrões:**
   Para cada chamada externa ou ponto crítico encontrado, avalie a aplicação dos seguintes padrões de resiliência com base nas tecnologias do projeto:

   ### A. Circuit Breaker (Disjuntor)
   - **Estados do Disjuntor:** Funciona como uma máquina de estados finitos com três estados normais (CLOSED, OPEN, HALF_OPEN) e três especiais (METRICS_ONLY, DISABLED, FORCED_OPEN).
   - **Janelas Deslizantes (Sliding Window):** Escolha entre janela baseada em contagem (count-based, ex: últimas N chamadas) ou baseada em tempo (time-based, ex: chamadas nos últimos N segundos) para armazenar e agregar os resultados.
   - **Thresholds de Erro e Chamadas Lentas:** O disjuntor abre quando a taxa de falha (failure rate) ou a porcentagem de chamadas lentas (slow calls) atingir o limite configurado (ex: >= 50%).
   - **Número Mínimo de Chamadas:** Configure o número mínimo de chamadas registradas (ex: `minimumNumberOfCalls` = 10) necessárias antes que a taxa de erro possa ser calculada e o circuito possa abrir.
   - **Configurações recomendadas (Resilience4j):**
     - `failureRateThreshold` (ex: 50%).
     - `minimumNumberOfCalls` (ex: 10).
     - `waitDurationInOpenState` (tempo de espera antes de mudar de OPEN para HALF_OPEN).
     - `permittedNumberOfCallsInHalfOpenState` (número de chamadas de teste permitidas no estado HALF_OPEN para validar a recuperação).
     - Lide adequadamente com a exceção `CallNotPermittedException` retornando um fallback amigável enquanto o circuito estiver OPEN.

   ### B. Retry (Tentativa de Repetição)
   - **Objetivo:** Recuperar a aplicação de falhas transientes e temporárias de rede.
   - **Instruções de Configuração:**
     - Defina um limite de tentativas (`maxAttempts`, ex: 3, onde o total é 1 tentativa inicial + 2 retentativas).
     - Configure o intervalo de espera inteligente (`IntervalFunction`) com Backoff Exponencial e Jitter (variação aleatória do tempo para evitar tempestades de requisições concorrentes sobrecarregando o sistema downstream).
     - Defina explicitamente quais exceções devem acionar o retry (`retryExceptions`) e quais devem ser ignoradas (`ignoreExceptions`).
     - Garanta que as operações decoradas sejam estritamente idempotentes.
     - Se for no Spring 7.0+, prefira o uso da anotação `@Retryable` ou do `RetryTemplate` programático.

   ### C. Bulkhead (Compartimentação)
   - **Objetivo:** Isolar recursos para que a falha em uma parte do sistema não consuma todos os recursos da aplicação (threads ou conexões) e derrube o sistema inteiro.
   - **Implementações (Resilience4j):**
     - `SemaphoreBulkhead`: Usa semáforos para limitar a concorrência direta de execuções simultâneas.
     - `FixedThreadPoolBulkhead`: Usa um pool de threads fixo e uma fila delimitada (bounded queue) para processamento assíncrono.
     - **Configuração chave:** `maxConcurrentCalls` e `maxWaitDuration`.
     - Se for no Spring 7.0+, use a anotação nativa `@ConcurrencyLimit` para proteger recursos de acesso simultâneo massivo (especialmente útil ao utilizar Virtual Threads).

   ### D. Rate Limiter (Limitador de Taxa) & Time Limiter
   - **Rate Limiter:** Controla o fluxo de requisições dividindo o tempo em ciclos (`limitRefreshPeriod`) e renovando as permissões disponíveis (`limitForPeriod`). Rejeita chamadas excedentes com `RequestNotPermitted`.
   - **Time Limiter:** Define limites de tempo de execução rígidos (`timeoutDuration`) para chamadas assíncronas (CompletableFuture ou Reactive Mono/Flux), com a opção de cancelar a tarefa futura em execução (`cancelRunningFuture=true`).

   ### E. Cache & Desacoplamento Stateless
   - **Cache:** Armazene resultados de consultas comuns em mecanismos de cache distribuído ou local para poupar o banco de dados. Evite usar a Implementação de Referência do JCache em produção devido a problemas concorrentes conhecidos; use Ehcache, Caffeine, Redisson, Hazelcast ou Ignite.
   - **Stateless:** Promova a remoção do estado de sessão da camada de aplicação (movendo dados de sessão para DynamoDB ou Redis) para viabilizar escalabilidade horizontal fluida.

3. **Validação com Engenharia do Caos:**
   - Recomende testes controlados e proativos inserindo falhas de forma intencional em ambientes controlados:
     - **Injeção de Latência (Latency Injection):** Emular redes lentas ou instáveis.
     - **Injeção de Falhas (Fault Injection):** Desligamento forçado de instâncias, simulação de falha de disco ou terminação de processos.
     - **Geração de Carga (Load Generation):** Estressar o sistema sob volumes massivos de tráfego.
     - **Controle do Blast Radius (Raio de Explosão):** Limite os testes a um subconjunto de serviços, execute por tempo limitado, fora dos horários de pico e preferencialmente em ambientes de desenvolvimento/pre-production que espelhem fielmente a produção.

4. **Entregável Esperado:**
   - Forneça uma comparação clara entre o código original (vulnerável) e o código melhorado.
   - Apresente blocos de configuração recomendados (como `application.yml` ou `application.properties`) com justificativas baseadas em limites do mundo real.
