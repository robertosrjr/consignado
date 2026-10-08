package br.com.consignado.platform.ingestion.infrastructure.adapter.out.crypto;

import br.com.consignado.platform.ingestion.application.port.out.PayloadProtectionPort;
import br.com.consignado.platform.shared.crypto.PayloadEnvelope;
import br.com.consignado.platform.shared.payload.InboxPayload;
import br.com.consignado.platform.shared.payload.InboxPayloadCodec;

/** Serializa o payload e o protege com criptografia de envelope (chave local ou KMS). */
public final class EnvelopePayloadProtectionAdapter implements PayloadProtectionPort {

  private final PayloadEnvelope envelope;

  public EnvelopePayloadProtectionAdapter(PayloadEnvelope envelope) {
    this.envelope = envelope;
  }

  @Override
  public String protect(InboxPayload payload) {
    return envelope.protect(InboxPayloadCodec.toBytes(payload));
  }
}
