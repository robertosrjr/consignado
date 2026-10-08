package br.com.consignado.platform.dispatcher.dispatch;

import static br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.KEY;
import static br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.insert;
import static br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.FakePublisher;
import br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.FakeUpdater;
import br.com.consignado.platform.shared.contract.Lane;
import org.junit.jupiter.api.Test;

class DispatchServiceTest {

  private final FakePublisher publisher = new FakePublisher();
  private final FakeUpdater updater = new FakeUpdater();
  private final DispatchService service = service(publisher, updater);

  @Test
  void should_publish_to_the_fast_lane_and_mark_dispatched_for_portability() {
    DispatchOutcome outcome = service.dispatch(insert("1", KEY, "banco-a", true, "1000.00"));

    assertThat(outcome.kind()).isEqualTo(DispatchOutcome.Kind.DISPATCHED);
    assertThat(outcome.lane()).isEqualTo(Lane.FAST);
    assertThat(publisher.published).hasSize(1);
    assertThat(publisher.published.get(0).lane()).isEqualTo(Lane.FAST);
    assertThat(publisher.published.get(0).message().key().toString()).isEqualTo(KEY);
    assertThat(publisher.published.get(0).message().priorityScore()).isEqualTo(60);
    assertThat(publisher.published.get(0).tracingId()).isEqualTo("trace-1");
    assertThat(updater.marked).hasSize(1);
  }

  @Test
  void should_publish_to_the_standard_lane_when_no_criterion_applies() {
    DispatchOutcome outcome = service.dispatch(insert("2", KEY, "banco-a", false, "1000.00"));

    assertThat(outcome.lane()).isEqualTo(Lane.STANDARD);
    assertThat(publisher.published.get(0).lane()).isEqualTo(Lane.STANDARD);
    assertThat(publisher.published.get(0).message().priorityScore()).isZero();
  }

  @Test
  void should_never_put_the_cpf_or_the_amount_in_the_published_message() {
    service.dispatch(insert("1"));

    String message = publisher.published.get(0).message().toString();
    assertThat(message).doesNotContain("1000").doesNotContain("cpf");
  }

  @Test
  void should_skip_records_that_are_not_inserts_or_not_in_received_status() {
    StreamRecordView modify =
        new StreamRecordView("MODIFY", "3", KEY, "banco-a", "INSS", true, "1.00", "t", "RECEIVED");
    StreamRecordView remove =
        new StreamRecordView("REMOVE", "4", KEY, "banco-a", "INSS", true, "1.00", "t", "RECEIVED");
    StreamRecordView dispatched =
        new StreamRecordView(
            "INSERT", "5", KEY, "banco-a", "INSS", true, "1.00", "t", "DISPATCHED");

    assertThat(service.dispatch(modify).kind()).isEqualTo(DispatchOutcome.Kind.SKIPPED);
    assertThat(service.dispatch(remove).kind()).isEqualTo(DispatchOutcome.Kind.SKIPPED);
    assertThat(service.dispatch(dispatched).kind()).isEqualTo(DispatchOutcome.Kind.SKIPPED);
    assertThat(publisher.published).isEmpty();
    assertThat(updater.marked).isEmpty();
  }

  @Test
  void should_reject_malformed_records_without_publishing() {
    assertThatThrownBy(() -> service.dispatch(insert("6", "nao-uuid", "banco-a", true, "1.00")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.dispatch(insert("7", KEY, "tenant invalido", true, "1.00")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.dispatch(insert("8", KEY, "banco-a", true, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                service.dispatch(
                    new StreamRecordView(
                        "INSERT", "9", KEY, "banco-a", "OUTRO", true, "1.00", "t", "RECEIVED")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(publisher.published).isEmpty();
  }

  @Test
  void should_not_mark_dispatched_when_publishing_fails() {
    publisher.failure = new IllegalStateException("sqs fora do ar");

    assertThatThrownBy(() -> service.dispatch(insert("1")))
        .isInstanceOf(IllegalStateException.class);

    assertThat(updater.marked).isEmpty();
  }

  @Test
  void should_fail_when_marking_fails_after_publishing_so_the_stream_retries() {
    updater.failure = new IllegalStateException("dynamo fora do ar");

    assertThatThrownBy(() -> service.dispatch(insert("1")))
        .isInstanceOf(IllegalStateException.class);

    assertThat(publisher.published).hasSize(1);
  }

  @Test
  void should_not_leak_item_data_in_the_record_to_string() {
    assertThat(insert("1").toString()).doesNotContain(KEY).doesNotContain("banco-a");
  }
}
