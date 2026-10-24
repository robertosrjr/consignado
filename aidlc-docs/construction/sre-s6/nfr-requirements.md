# S6 — Capacidade local

Decisões herdadas: tudo local, Java 25, estado do worker em DynamoDB Local, sem Docker ou AWS. Meta de 10 mil TPS permanece não homologada.

- [x] Envelope inicial: 10, 30, 60, 120 e 240 requisições/s, 15 segundos por patamar, replay de uma em cada cinco requisições, lanes alternadas. O último patamar supera o teto nominal do legado em entradas únicas e permite observar drenagem.
- [x] Medir vazão efetiva, p95/p99, códigos HTTP, propostas únicas e estado terminal conciliado pela chave.
- [x] Executar após os testes de S5; interromper progressão com erro ou drenagem incompleta. Ensaio inicial FAIL por 503; repetição controlada alcançou os oito patamares, com FAIL na drenagem do último.
- [x] Publicar resultados observados, distinguindo ensaio curto de capacidade sustentada: `docs/sre/capacidade-local.md`. Nenhum limite homologado; p99 do servidor falhou mesmo na confirmação conservadora.

Modelo: entrada nova = vazão HTTP / fator de replay; fator = total de requisições / propostas únicas. Drenagem ideal = backlog / vazão terminal observada; sob entrada contínua = backlog / (vazão terminal - entrada nova), somente quando o denominador for positivo. O teto nominal de 180 TPS do legado é limite superior, não resultado do ensaio.
