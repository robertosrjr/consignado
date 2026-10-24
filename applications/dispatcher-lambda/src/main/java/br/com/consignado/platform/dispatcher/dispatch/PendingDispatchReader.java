package br.com.consignado.platform.dispatcher.dispatch;

import java.util.List;

/** Porta: itens aceitos e ainda nao despachados, criados ate um instante (candidatos a orfaos). */
public interface PendingDispatchReader {

  /**
   * @return ate {@code limit} itens como registros de insercao (mesma forma do Stream)
   */
  List<StreamRecordView> pendingCreatedUpTo(long createdAtOrBeforeEpochMillis, int limit);
}
