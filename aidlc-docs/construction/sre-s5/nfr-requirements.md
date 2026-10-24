# S5 — Requisitos não funcionais

- [x] Decisões aprovadas reaproveitadas: Java 25, DynamoDB Local, ElasticMQ, sem Docker e sem AWS.
- [x] Correção: commit atômico de estado/eventos; idempotência de criação; terminal absorvente; chave financeira preservada no reenvio.
- [x] Segurança: nenhuma informação pessoal adicionada ao estado; evidências sem payload cifrado ou credenciais.
- [x] Verificação: build completo `mvn -o verify -Pintegration` com cache local explícito: 10 módulos, 473 testes, zero falhas/erros/ignorados; gates existentes aprovados.

Disponibilidade local limitada à vida do emulador em memória. O objetivo desta onda é recuperação do worker, não recuperação do disco ou desastre da infraestrutura.
