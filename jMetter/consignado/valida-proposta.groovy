import groovy.json.JsonSlurper

// Usar vars/props, nunca interpolacao ${...} dentro de scripts compilados.
try {
    assert prev.getResponseCode() == '202': 'esperado HTTP 202; recebido ' + prev.getResponseCode()
    def body = new JsonSlurper().parseText(prev.getResponseDataAsString())
    assert vars.get('expected_status').split('[|]').contains(body.status): 'status inesperado: ' + body.status
    assert body.idempotencyKey == vars.get('key'): 'Idempotency-Key diferente da enviada'
    assert body.tracingId instanceof String && !body.tracingId.isBlank(): 'tracingId ausente'
    assert body.receivedAt instanceof Number && body.receivedAt > 0: 'receivedAt invalido'
    if (vars.get('business_case') == 'true' && body.status == 'RECEIVED') {
        long cents = new BigDecimal(vars.get('amount')).movePointRight(2).longValueExact()
        props.get('business_received').incrementAndGet()
        props.get('business_cents').addAndGet(cents)
    }
    if (vars.get('business_case') == 'true' && body.status == 'DUPLICATE_ACCEPTED') {
        props.get('business_duplicates').incrementAndGet()
    }
} catch (Throwable error) {
    AssertionResult.setFailure(true)
    AssertionResult.setFailureMessage(error.message ?: error.class.simpleName)
}
