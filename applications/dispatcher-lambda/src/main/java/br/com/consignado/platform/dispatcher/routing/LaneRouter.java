package br.com.consignado.platform.dispatcher.routing;

import br.com.consignado.platform.shared.contract.Lane;

/** Decide a lane pelo escore: a partir do limiar, Fast Lane; caso contrario, Standard. */
public final class LaneRouter {

  private final int fastThreshold;

  public LaneRouter(int fastThreshold) {
    this.fastThreshold = fastThreshold;
  }

  public Lane route(int priorityScore) {
    return priorityScore >= fastThreshold ? Lane.FAST : Lane.STANDARD;
  }
}
