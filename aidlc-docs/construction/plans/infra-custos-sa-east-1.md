# Infraestrutura AWS e Modelo de Cobranca - sa-east-1 (Sao Paulo)

**Fonte dos precos**: lista de precos publica da AWS (`pricing.us-east-1.amazonaws.com`, ofertas AmazonEC2, AWSELB, awswaf, AmazonDynamoDB, AWSQueueService, AWSLambda, awskms, AmazonCloudWatch, AWSSecretsManager, AmazonVPC, AmazonS3, regiao `sa-east-1`), consultada em 2026-10-07. Valores em **USD, sob demanda, sem impostos**; precos mudam, confirme na calculadora da AWS antes de aplicar.

**O que NAO foi verificado** (marcado com "(nv)"): camadas gratuitas mensais, cobranca por IP do ALB, preco do AWS Budgets, gratuidade do SSM Session Manager e das leituras do DynamoDB Streams feitas pela Lambda.

**Premissas de dimensionamento (minhas, ainda nao decididas)**: Linux x86, sob demanda, uma AZ para as EC2, 730 h/mes. Dois perfis:
- **Enxuto (desenvolvimento)**: aplicacao `c6i.xlarge`, observabilidade `t3.large`, Elasticsearch `r6i.large`; EBS 20 + 30 + 60 GB.
- **Carga**: aplicacao `c6i.2xlarge`, observabilidade `m6i.large`, Elasticsearch `r6i.large`; EBS 30 + 50 + 100 GB. Nao ha garantia de que uma unica instancia sustente 10.000 TPS; o k6 vai medir.

---

## 1. Lista de recursos e modelo de cobranca

### 1.1 Computacao

| Recurso | Funcao | Modelo de cobranca | Preco sa-east-1 | Cobra quando parado/desligado? |
|---|---|---|---|---|
| EC2 de aplicacao | `ingestion-api`, `worker`, `mock-legacy` (systemd) | Por hora de instancia em execucao | c6i.xlarge **$0,262/h**; c6i.2xlarge **$0,524/h**; (ARM c7g.2xlarge $0,4443/h) | **Nao** (so o EBS) |
| EC2 de observabilidade | Prometheus, Grafana, Pushgateway, Jaeger | Idem | t3.large **$0,1344/h**; m6i.large **$0,153/h** | **Nao** (so o EBS) |
| EC2 do Elasticsearch | Logs e traces | Idem | r6i.large **$0,201/h** (16 GiB) | **Nao** (so o EBS) |
| AWS Lambda (Dispatcher) | Roteamento QoS (DynamoDB Streams para SQS) | Por requisicao + por GB-segundo | **$0,20 por milhao** de requisicoes; **$0,0000166667 por GB-s** (x86) ou **$0,0000133334 por GB-s** (ARM) | Nao (so quando invocada); camada gratuita (nv) |

### 1.2 Armazenamento de bloco

| Recurso | Modelo de cobranca | Preco | Cobra quando parado? |
|---|---|---|---|
| EBS gp3 (discos das 3 EC2) | Por GB provisionado por mes (independe de a EC2 estar ligada) | **$0,152 por GB-mes** | **Sim**, continua |
| Snapshots / AMIs (se usados) | Por GB-mes armazenado | **$0,068 por GB-mes** | Sim |

### 1.3 Rede e borda

| Recurso | Funcao | Modelo de cobranca | Preco | Cobra quando "desligado"? |
|---|---|---|---|---|
| VPC, sub-redes, tabelas de rota, Internet Gateway, security groups | Rede | Sem cobranca | $0 | n/a |
| **NAT Gateway** | Saida das EC2 privadas (SQS, KMS, SSM, pacotes) | **Por hora de existencia** + **por GB processado** | **$0,093/h** (~$67,89/mes) + **$0,093/GB** | **Sim, enquanto existir** (so deixa de cobrar se for destruido) |
| IPv4 publico (IP elastico do NAT; IPs do ALB, assumindo 2 AZs (nv)) | Enderecamento publico | Por hora, em uso ou ocioso | **$0,005/h** cada (~$3,65/mes) | Sim, enquanto existir |
| VPC endpoint de gateway (DynamoDB, S3) | Acesso privado e gratuito ao DynamoDB | Sem cobranca | $0 | n/a |
| VPC endpoint de interface (SQS, KMS, SSM...) - **nao usado no desenho C** | Acesso privado | Por hora por AZ + por GB | **$0,021/h** (~$15,33/mes) por endpoint/AZ + **$0,01/GB** | Sim |
| **ALB** | Borda HTTPS na frente da EC2 | **Por hora** + **por LCU-hora** (conexoes, banda, regras) | **$0,034/h** (~$24,82/mes) + **$0,011 por LCU-hora** | **Sim, enquanto existir** (nao pode ser parado) |
| **WAF v2** | Rate limit por IP e por `X-Tenant-ID` | Por web ACL/mes (proporcional por hora) + por regra/mes + por milhao de requisicoes | **$5,00** por web ACL; **$1,00** por regra; **$0,60 por milhao** de requisicoes | Sim, enquanto existir |
| Transferencia de dados | Saida para a internet; entre AZs | Por GB | Saida internet: 1 GB gratis, depois **$0,25/GB** (ate 10 TB); entre AZs/IP publico na regiao: **$0,01/GB** | Somente com trafego |

### 1.4 Dados, mensageria e seguranca

| Recurso | Funcao | Modelo de cobranca | Preco | Cobra quando ocioso? |
|---|---|---|---|---|
| DynamoDB sob demanda (`DurableInbox`) | Buffer duravel e idempotencia | Por unidade de escrita (1 por 1 KB) e de leitura (1 por 4 KB); armazenamento por GB-mes | Escrita **$0,9375 por milhao**; leitura **$0,1875 por milhao**; armazenamento **$0 ate 25 GB**, depois **$0,375/GB-mes** | Nao (so armazenamento acima de 25 GB) |
| DynamoDB Streams | Gatilho do Dispatcher | Por unidade de leitura de Stream | 2,5 milhoes gratis/mes, depois **$0,0000003 por unidade**; leituras feitas pela Lambda nao cobradas (nv) | Nao |
| SQS (fast, standard, DLQ) | Filas QoS | Por requisicao (send, receive, delete; cada lote de ate 10 mensagens conta 1) | **$0,40 por milhao** (standard); 1 milhao gratis/mes | Nao |
| KMS | Cifra do CPF (chave gerenciada) | Por chave/mes + por requisicao | **$1,00 por chave-mes**; **$0,03 por 10.000 requisicoes**; 20.000 requisicoes gratis/mes | **Sim** ($1/mes por chave; exclusao leva no minimo 7 dias) |
| Secrets Manager | Credenciais do Elasticsearch | Por segredo/mes + por 10.000 chamadas de API | **$0,40 por segredo-mes**; **$0,05 por 10.000 chamadas** | **Sim** |
| SSM Session Manager | Acesso administrativo sem SSH | Sem cobranca adicional (nv); depende de saida pelo NAT | $0 (nv) | n/a |
| IAM | Perfis e politicas | Sem cobranca | $0 | n/a |
| AWS Budgets | Alerta de custo | Primeiros orcamentos gratuitos (nv) | (nv) | n/a |

### 1.5 Logs, metricas e armazenamento de objetos

| Recurso | Funcao | Modelo de cobranca | Preco | Cobra quando ocioso? |
|---|---|---|---|---|
| CloudWatch Logs | Logs da Lambda (e do que mais enviar) | Ingestao por GB + armazenamento por GB-mes | Ingestao **$0,90/GB**; armazenamento **$0,0408/GB-mes** | So armazenamento |
| CloudWatch metricas customizadas (EMF) | Metricas do Dispatcher | Por metrica-mes | **$0,30** (primeiras 10.000) | Sim, por metrica existente |
| CloudWatch alarmes | Alertas | Por alarme-mes | **$0,135** | Sim |
| S3 (logs do ALB, estado do Terraform, se usados) | Objetos | Por GB-mes + por requisicao | **$0,0405/GB-mes**; PUT **$0,007 por 1.000**; GET **$0,0056 por 10.000** | So armazenamento |

---

## 2. Cenarios (calculados com os precos acima)

### 2.1 Custo por hora de ambiente "ligado" (compute + rede + borda)
| Item | Perfil enxuto | Perfil carga |
|---|---|---|
| 3 EC2 | $0,5974/h | $0,8780/h |
| NAT Gateway | $0,093/h | $0,093/h |
| ALB (sem LCU) | $0,034/h | $0,034/h |
| 3 IPv4 publicos (NAT + 2 do ALB, nv) | $0,015/h | $0,015/h |
| WAF (1 ACL + 3 regras, proporcional) | ~$0,011/h | ~$0,011/h |
| **Total aproximado** | **~$0,75/h** | **~$1,03/h** |

### 2.2 Sessao de 4 horas (ligar, testar, desligar)
- Perfil enxuto: EC2 $2,39 + rede/borda $0,61 = **~$3,0**.
- Perfil carga: EC2 $3,51 + rede/borda $0,61 = **~$4,1**.
- Mais o consumo de dados da execucao (secao 2.4) e o EBS do mes (secao 2.3).

### 2.3 Custo mensal com o ambiente **desligado** (EC2 paradas, NAT e ALB removidos)
| Item | Perfil enxuto | Perfil carga |
|---|---|---|
| EBS gp3 (110 GB / 180 GB) | $16,72 | $27,36 |
| KMS (1 chave) | $1,00 | $1,00 |
| Secrets Manager (2 segredos) | $0,80 | $0,80 |
| DynamoDB, SQS, Lambda ociosos | ~$0 | ~$0 |
| **Total aproximado** | **~$18,5/mes** | **~$29/mes** |

(Snapshots/AMIs, S3 e CloudWatch somam centavos a poucos dolares, conforme o uso.)

### 2.4 Uma execucao de 3 milhoes de propostas (DoD, escala cheia)
| Item | Estimativa |
|---|---|
| DynamoDB escritas (put condicional + atualizacao de status) | ~$5,62 |
| DynamoDB leituras (Worker) | ~$0,56 |
| SQS (sem batching, send + receive + delete, menos 1 milhao gratis) | ~$3,20 (menos com batching) |
| Lambda (lotes de 10, 256 MB, 200 ms) | ~$0,32 |
| WAF (requisicoes) | ~$1,80 |
| KMS **sem cache de chave de dados** (1 chamada por proposta) | ~$9,00 (cai a centavos com envelope encryption e cache) |
| NAT (dados processados, ex.: 10 GB de SQS/KMS) | ~$0,93 |
| **Total aproximado** | **~$22** (cerca de $13 com KMS em cache) |

Observacao: o valor de requisicoes do ALB (LCU) nao esta incluido; depende de conexoes, banda e regras.

### 2.5 Custo se esquecer o ambiente ligado 24x7 (730 h)
| Item | Perfil enxuto | Perfil carga |
|---|---|---|
| 3 EC2 | $436,10 | $640,94 |
| NAT + ALB + 3 IPv4 + WAF | $111,66 | $111,66 |
| EBS | $16,72 | $27,36 |
| **Total aproximado** | **~$565/mes** | **~$780/mes** |

Leitura: **as EC2 sao o maior item** ($436 a $641/mes), mas parar as EC2 nao basta: NAT + ALB + WAF + IPv4 somam **~$112/mes** e so deixam de cobrar se forem removidos. O modo liga/desliga (`environment_on`) cobre os dois.

---

## 3. Pontos de atencao
1. **O NAT Gateway em sa-east-1 custa $0,093/h (~$67,89/mes) mais $0,093/GB**, valor acima do que eu estimara antes. Reforca a decisao de remove-lo no modo desligado.
2. Discos EBS cobrem **$0,152/GB-mes mesmo com a EC2 parada**: reduzir o tamanho dos volumes reduz o custo fixo.
3. **KMS por requisicao** pesa em carga: usar envelope encryption com cache de chave de dados (decisao de design de U3).
4. `sa-east-1` e mais cara que `us-east-1` para varios itens; a regiao fica a seu criterio (requisito Q17), mas dados de clientes brasileiros favorecem `sa-east-1` (LGPD).
5. Impostos, IOF e conversao cambial nao estao incluidos.
6. Estes valores nao substituem a calculadora da AWS nem o Cost Explorer apos o primeiro `apply`.
