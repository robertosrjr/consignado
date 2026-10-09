package br.com.consignado.platform.worker.application.port.out;

/**
 * Porta de saida: disponibilidade do legado para o laco de despacho (back-pressure). Com o legado
 * indisponivel, o laco deixa as mensagens na fila em vez de retira-las: uma recusa local do
 * circuito nao pode consumir tentativa de entrega nem levar proposta valida para a DLQ.
 */
public interface LegacyAvailabilityPort {

  /** Sempre disponivel (sem circuit breaker). */
  LegacyAvailabilityPort ALWAYS_AVAILABLE = () -> Availability.AVAILABLE;

  Availability availability();

  /** Estado visto pelo laco de despacho. */
  enum Availability {
    /** Trafego normal. */
    AVAILABLE,
    /** Legado em teste (circuito semi-aberto): uma chamada de prova por vez. */
    PROBING,
    /** Legado protegido (circuito aberto): nada sai da fila. */
    UNAVAILABLE
  }
}
