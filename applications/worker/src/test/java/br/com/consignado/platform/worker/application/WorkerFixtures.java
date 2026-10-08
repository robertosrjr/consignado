package br.com.consignado.platform.worker.application;

import br.com.consignado.platform.shared.contract.ConvenioType;
import br.com.consignado.platform.shared.contract.Lane;
import br.com.consignado.platform.shared.contract.ProposalMessage;
import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.shared.vo.Cpf;
import br.com.consignado.platform.shared.vo.IdempotencyKey;
import br.com.consignado.platform.shared.vo.Money;
import br.com.consignado.platform.shared.vo.TenantId;
import br.com.consignado.platform.worker.application.port.out.ClockPort;
import br.com.consignado.platform.worker.application.port.out.InboxRecord;
import br.com.consignado.platform.worker.application.port.out.LegacyCorePort;
import br.com.consignado.platform.worker.application.port.out.LegacySubmission;
import br.com.consignado.platform.worker.application.port.out.QueuedMessage;
import br.com.consignado.platform.worker.domain.LegacyResult;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Fakes e dados de teste compartilhados pelos testes de aplicacao do Worker. */
final class WorkerFixtures {

  static final IdempotencyKey KEY = IdempotencyKey.parse("e3b0c442-98fc-11ee-b9d1-0242ac120002");
  static final TenantId TENANT = new TenantId("banco-a");

  private WorkerFixtures() {}

  static ProposalMessage message(IdempotencyKey key, Lane lane) {
    return ProposalMessage.of(key, TENANT, lane, lane == Lane.FAST ? 60 : 0);
  }

  static ProposalMessage message() {
    return message(KEY, Lane.FAST);
  }

  static InboxRecord inboxRecord(IdempotencyKey key) {
    return new InboxRecord(
        key,
        TENANT,
        InboxPayload.of(new Cpf("52998224725"), ConvenioType.INSS, false, Money.of("1000.00"), 12),
        "trace-1");
  }

  static final class FixedClock implements ClockPort {
    @Override
    public Instant now() {
      return Instant.parse("2026-10-08T12:00:00Z");
    }
  }

  /** Legado roteirizado: devolve os resultados da fila; sem roteiro, confirma. */
  static final class ScriptedLegacy implements LegacyCorePort {
    final Deque<LegacyResult> script = new ArrayDeque<>();
    final List<LegacySubmission> calls = new ArrayList<>();
    RuntimeException failure;

    @Override
    public LegacyResult submit(LegacySubmission submission) {
      calls.add(submission);
      if (failure != null) {
        throw failure;
      }
      LegacyResult next = script.poll();
      return next != null ? next : new LegacyResult.Confirmed("av-default");
    }
  }

  record TestMessage(Lane lane, ProposalMessage message, int receiveCount)
      implements QueuedMessage {}
}
