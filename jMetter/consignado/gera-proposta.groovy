// Gera uma proposta nova por requisicao: parceiro, token, chave, CPF valido e dados de negocio.
// Usado pelos cenarios 01 (fumaca) e 02 (carga). Os tokens vem do setUp (props token_banco-NNN).
import java.util.concurrent.ThreadLocalRandom

def random = ThreadLocalRandom.current()

// Parceiro: distribui as threads entre os parceiros para nao estourar a cota de 100 req/s de cada um
int tenants = props.getProperty('tenants', '20') as int
String tenant = String.format('banco-%03d', (ctx.getThreadNum() % tenants) + 1)
vars.put('tenant', tenant)
vars.put('token', props.get('token_' + tenant))
vars.put('key', UUID.randomUUID().toString())
vars.put('expected_status', 'RECEIVED')
vars.put('business_case', 'false')

// CPF sintetico com digitos validos; nao usar como dado de cliente.
int[] d = new int[11]
for (int i = 0; i < 9; i++) {
    d[i] = random.nextInt(10)
}
int sum = 0
for (int i = 0; i < 9; i++) {
    sum += d[i] * (10 - i)
}
d[9] = (sum % 11) < 2 ? 0 : 11 - (sum % 11)
sum = 0
for (int i = 0; i < 10; i++) {
    sum += d[i] * (11 - i)
}
d[10] = (sum % 11) < 2 ? 0 : 11 - (sum % 11)
vars.put('cpf', d.join(''))

// Negocio: ~37% acima de R$ 50.000 e ~20% portabilidade, para exercitar as duas lanes
vars.put('amount', String.format(Locale.ROOT, '%.2f', 1000 + random.nextDouble() * 79000))
vars.put('portability', random.nextInt(100) < 20 ? 'true' : 'false')
vars.put('installments', String.valueOf(12 + random.nextInt(73)))
vars.put('convenio', ['INSS', 'ESOCIAL_CLT', 'SIAPE_PUBLICO'][random.nextInt(3)])
