package br.com.consignado.platform.worker.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import br.com.consignado.platform.worker.WorkerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Sem a chave do legado, sem a URL ou sem as filas o servico nao sobe (nada de padroes inseguros).
 */
class RequiredConfigurationTest {

  private static Throwable start(String... properties) {
    return catchThrowable(
        () ->
            new SpringApplicationBuilder(WorkerApplication.class)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .properties("server.port=0")
                .run());
  }

  @Test
  void should_fail_startup_without_the_legacy_api_key() {
    assertThat(
            start(
                "worker.queue.type=memory",
                "worker.inbox.type=memory",
                "worker.legacy.base-url=http://127.0.0.1:1",
                "worker.legacy.api-key="))
        .isNotNull();
  }

  @Test
  void should_fail_startup_without_the_legacy_url() {
    assertThat(
            start(
                "worker.queue.type=memory",
                "worker.inbox.type=memory",
                "worker.legacy.base-url=",
                "worker.legacy.api-key=k"))
        .isNotNull();
  }

  @Test
  void should_fail_startup_with_sqs_queues_but_no_queue_urls() {
    assertThat(
            start(
                "worker.queue.type=sqs",
                "worker.inbox.type=memory",
                "worker.legacy.base-url=http://127.0.0.1:1",
                "worker.legacy.api-key=k",
                "aws.region=sa-east-1"))
        .isNotNull();
  }

  @Test
  void should_fail_startup_in_dynamodb_local_mode_without_the_payload_key() {
    assertThat(
            start(
                "worker.queue.type=memory",
                "worker.inbox.type=dynamodb",
                "worker.payload.local-key=",
                "worker.legacy.base-url=http://127.0.0.1:1",
                "worker.legacy.api-key=k"))
        .isNotNull();
  }
}
