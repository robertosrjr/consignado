package br.com.consignado.platform.dispatcher.dispatch;

import br.com.consignado.platform.shared.contract.Lane;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;

/**
 * Processa um lote com falha parcial: registros que falham sao devolvidos pelo numero de sequencia
 * (batchItemFailures), para o Stream repetir somente a partir deles. Nada de PII nos logs.
 */
public final class BatchDispatcher {

  private static final Logger LOG = System.getLogger(BatchDispatcher.class.getName());

  private final DispatchService service;

  public BatchDispatcher(DispatchService service) {
    this.service = service;
  }

  public BatchResult dispatch(List<StreamRecordView> records) {
    int fast = 0;
    int standard = 0;
    int skipped = 0;
    List<String> failed = new ArrayList<>();
    for (StreamRecordView record : records) {
      try {
        DispatchOutcome outcome = service.dispatch(record);
        if (outcome.kind() == DispatchOutcome.Kind.SKIPPED) {
          skipped++;
        } else if (outcome.lane() == Lane.FAST) {
          fast++;
        } else {
          standard++;
        }
      } catch (RuntimeException e) {
        LOG.log(
            Level.ERROR,
            "event=dispatch_failed sequence={0} error={1}",
            record.sequenceNumber(),
            e.getClass().getSimpleName());
        failed.add(record.sequenceNumber());
      }
    }
    return new BatchResult(fast, standard, skipped, List.copyOf(failed));
  }
}
