# S1 — SLOs formais

Data: 23/10/2026. Entrega documental (RF-04): `docs/sre/slo.md`, referenciado em `docs/sre/README.md`.

- J1: SLO-J1-A 99,5%/30d; SLO-J1-L 95% <= 100 ms e 99% <= 200 ms; exclusões 400/422, 401/403 e 429 por cota excedida.
- J2: fast 95%/2 min; standard 90%/45 min; unrouted como controle; denominador por prazo vencido.
- Política de error budget em quatro faixas, mais o invariante financeiro fora do budget.
- Restrição física: 180 TPS, ou seja, cerca de 486 mil propostas standard por prazo de 45 min.
- Lacunas registradas para a S2: buckets de 120 s/2700 s, avaliador de prazos, buckets HTTP de 100/200 ms, contador de conciliação.

Validação: conferência dos nomes e buckets reais (`BusinessMetrics`, `application.yml` da ingestion-api). Nenhum código alterado.
