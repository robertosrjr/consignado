// Fixture HTTP local: valida o plano, nao o negocio real nem JWT/infraestrutura.
// Executar com Java 21 e o classpath lib/* do JMeter (ver LEIA-ME.md).
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

File base = new File(args[0]).canonicalFile
def mode = new AtomicReference('ok')
def proposals = new ConcurrentHashMap<String, Map>()
def executor = Executors.newCachedThreadPool()
def server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
server.executor = executor
server.createContext('/') { exchange ->
    int code = 200
    def response
    if (exchange.requestURI.path == '/token') {
        response = mode.get() == 'bad-token' ? [:] : [access_token:'fixture-token']
    } else if (exchange.requestURI.path == '/v1/proposals') {
        def body = new JsonSlurper().parseText(exchange.requestBody.getText('UTF-8'))
        String key = exchange.requestHeaders.getFirst('Idempotency-Key')
        assert body.amount > 0 && body.customerCpf.size() == 11
        def previous = proposals.putIfAbsent(key, [amount:body.amount])
        code = 202
        response = [status:previous && mode.get() != 'bad-replay' ? 'DUPLICATE_ACCEPTED' : 'RECEIVED',
                    idempotencyKey:key,tracingId:'fixture-trace',receivedAt:System.currentTimeMillis().intdiv(1000)]
    } else { code = 404; response = [error:'not found'] }
    byte[] bytes = JsonOutput.toJson(response).getBytes('UTF-8')
    exchange.responseHeaders.set('Content-Type','application/json')
    exchange.sendResponseHeaders(code, bytes.length)
    exchange.responseBody.withCloseable { it.write(bytes) }
}
server.start()
int port = server.address.port
def run = { String scenario, List extra, boolean shouldPass ->
    proposals.clear()
    def command = ['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',new File(base,'executar.ps1').path,
                   '-Cenario',scenario,'-ApiPort',port.toString(),'-TokenPort',port.toString(),'-Tenants','1'] + extra
    def process = new ProcessBuilder(command).redirectErrorStream(true).start()
    process.outputStream.close()
    String output = process.inputStream.getText('UTF-8')
    int exit = process.waitFor()
    println "${scenario} / ${mode.get()}: exit=${exit}, propostas unicas=${proposals.size()}"
    if ((exit == 0) != shouldPass) { println output; throw new AssertionError('Resultado inesperado: ' + scenario) }
}
try {
    if (args.length > 1 && args[1] == 'Carga') {
        run('Carga', ['-Threads','2','-Rps','2','-Duracao','5','-Rampa','0','-MaxMs','5000'], true)
        assert proposals.size() > 0 && proposals.size() <= 14: 'Rajada acima do alvo de 2 req/s em 5 s'
        println 'OK: timer compartilhado respeitou o limite do teste curto.'
        return
    }
    run('Validar', [], true)
    run('Fumaca', ['-Quantidade','3'], true)
    assert proposals.size() == 3
    run('Negocio', ['-Quantidade','10','-Valor','1000.00'], true)
    assert proposals.size() == 10
    assert proposals.values().sum { it.amount } == new BigDecimal('10000.00')
    run('Duplicatas', ['-Threads','8'], true)
    assert proposals.size() == 1
    run('Carga', ['-Threads','2','-Rps','2','-Duracao','5','-Rampa','0','-MaxMs','5000'], true)
    assert proposals.size() > 0 && proposals.size() <= 14: 'Rajada acima do alvo de 2 req/s em 5 s'
    mode.set('bad-replay')
    run('Negocio', ['-Quantidade','2'], false)
    mode.set('bad-token')
    run('Fumaca', ['-Quantidade','1'], false)
    println 'OK: validacao, fumaca, negocio, duplicatas, carga e duas falhas deliberadas.'
} finally { server.stop(0); executor.shutdownNow() }
