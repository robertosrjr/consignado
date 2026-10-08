package br.com.consignado.platform.worker.application.port.out;

import br.com.consignado.platform.shared.vo.IdempotencyKey;
import java.util.Optional;

/** Porta de saida: leitura do item completo no DurableInbox (o CPF so e decifrado no adaptador). */
public interface InboxReadPort {

  /**
   * @return o item, ou vazio se nao existir (por exemplo, expirado pelo TTL)
   */
  Optional<InboxRecord> load(IdempotencyKey key);
}
