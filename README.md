# Platform Engine — PoC de Crédito Consignado

![Java](https://img.shields.io/badge/Java-25_LTS-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![Status](https://img.shields.io/badge/status-PoC_em_desenvolvimento-yellow)
![SRE](https://img.shields.io/badge/SRE_PoC-concluida-2ea44f)

Plataforma de ingestão assíncrona de propostas de crédito: aceita pedidos na borda e controla o envio ao legado, com filas prioritárias, idempotência e observabilidade.

Fluxo: **API → DynamoDB → dispatcher → SQS → worker → legado**.

Metas da PoC: **10.000 req/s na entrada**, **p95 abaixo de 100 ms** e processamento limitado a **180 TPS no legado**. A homologação completa permanece pendente; veja o [estado atual e as limitações](docs/estado-atual.md).

## Executar localmente

Pré-requisitos: **JDK 25 e Maven 3.9+**. As aplicações rodam sem Docker local.

```powershell
cd applications
mvn install -DskipTests
.\scripts\local-up.ps1
```

API: [Swagger](http://127.0.0.1:8080/swagger-ui.html). Token de teste: `http://127.0.0.1:8082/token?tenant=banco-a`.

Para encerrar, execute `.\scripts\local-down.ps1` na pasta `applications`.

## Documentação

| Assunto | Guia |
|---|---|
| Fechamento da PoC de SRE | [Conclusão, evidências, limites e backlog](docs/sre/fechamento-poc-sre-observabilidade.md) |
| Contexto de negócio e metas | [Contexto e objetivos](docs/narrativas/contexto-e-objetivos.md) |
| Alinhamento ao BIAN v14.0 | [Service Domains, cenário e lacunas](docs/narrativas/alinhamento-bian.md) |
| Serviços de negócio e ofertas (TAGF) | [Modelagem de serviços e ofertas](docs/narrativas/modelagem-servicos-e-ofertas.md) |
| Arquitetura, stack e decisões | [Visão técnica](docs/manuais/visao-tecnica.md) |
| Build, módulos e testes | [Aplicações](applications/README.md) |
| Ambiente local e envio de métricas | [Ambiente local](docs/manuais/ambiente-local.md) |
| Jornada de um pedido | [Negócio](docs/jornadas/jornada-da-proposta.md) · [Fluxo técnico](docs/jornadas/jornada-entre-modulos.md) |
| Integração HTTP | [Manual da API](docs/narrativas/manual-api-rest.md) |
| Grafana e métricas de negócio | [Dashboards](applications/observability/grafana/dashboards/README.md) |
| SRE, métricas, SLIs e SLOs | [Guia SRE e plano de implementação](docs/sre/README.md) |
| EC2, Docker e Terraform | [Infraestrutura](infrastructure/README.md) |
| Alertas e notificações | [Alertmanager → SNS → SQS](infrastructure/Configuration/observability/ALERTAS.md) |
| Evolução da idempotência | [Business Fingerprint v2](docs/business-fingerprint-v2-implementacao.md) |
| Entregas e pendências | [Estado atual](docs/estado-atual.md) |

[Índice completo da documentação](docs/README.md), incluindo especificações, auditorias e propostas de evolução.
