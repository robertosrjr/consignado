package br.com.consignado.platform.dispatcher.dispatch;

import br.com.consignado.platform.shared.contract.Lane;

/** Resultado do despacho de um registro. */
public record DispatchOutcome(Kind kind, Lane lane) {

  /** Tipo do resultado. */
  public enum Kind {
    DISPATCHED,
    SKIPPED
  }

  public static DispatchOutcome dispatched(Lane lane) {
    return new DispatchOutcome(Kind.DISPATCHED, lane);
  }

  public static DispatchOutcome skipped() {
    return new DispatchOutcome(Kind.SKIPPED, null);
  }
}
