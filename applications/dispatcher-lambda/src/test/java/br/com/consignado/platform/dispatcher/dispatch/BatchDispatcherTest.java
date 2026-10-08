package br.com.consignado.platform.dispatcher.dispatch;

import static br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.KEY;
import static br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.insert;
import static br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.service;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.FakePublisher;
import br.com.consignado.platform.dispatcher.dispatch.DispatchFixtures.FakeUpdater;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchDispatcherTest {

  private final FakePublisher publisher = new FakePublisher();
  private final FakeUpdater updater = new FakeUpdater();
  private final BatchDispatcher dispatcher = new BatchDispatcher(service(publisher, updater));

  @Test
  void should_count_dispatched_by_lane_and_skipped_records() {
    BatchResult result =
        dispatcher.dispatch(
            List.of(
                insert("1", KEY, "banco-a", true, "1.00"),
                insert("2", "0b0c0d0e-0000-4000-8000-000000000001", "banco-a", false, "1.00"),
                new StreamRecordView(
                    "REMOVE", "3", KEY, "banco-a", "INSS", true, "1.00", "t", "RECEIVED")));

    assertThat(result.dispatchedFast()).isEqualTo(1);
    assertThat(result.dispatchedStandard()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.failed()).isZero();
  }

  @Test
  void should_report_only_the_failing_sequence_numbers_and_keep_processing_the_rest() {
    BatchResult result =
        dispatcher.dispatch(
            List.of(
                insert("10", KEY, "banco-a", true, "1.00"),
                insert("11", "nao-uuid", "banco-a", true, "1.00"),
                insert("12", "0b0c0d0e-0000-4000-8000-000000000002", "banco-a", true, "1.00")));

    assertThat(result.failedSequenceNumbers()).containsExactly("11");
    assertThat(result.dispatchedFast()).isEqualTo(2);
    assertThat(publisher.published).hasSize(2);
  }

  @Test
  void should_report_every_record_as_failed_when_the_queue_is_down() {
    publisher.failure = new IllegalStateException("sqs fora do ar");

    BatchResult result =
        dispatcher.dispatch(
            List.of(
                insert("20"),
                insert("21", "0b0c0d0e-0000-4000-8000-000000000003", "banco-a", true, "1.00")));

    assertThat(result.failedSequenceNumbers()).containsExactly("20", "21");
    assertThat(result.dispatchedFast()).isZero();
  }

  @Test
  void should_return_an_empty_result_for_an_empty_batch() {
    BatchResult result = dispatcher.dispatch(List.of());

    assertThat(result.failed()).isZero();
    assertThat(result.dispatchedFast() + result.dispatchedStandard() + result.skipped()).isZero();
  }
}
