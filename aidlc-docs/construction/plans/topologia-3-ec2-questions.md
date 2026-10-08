# Topologia em 3 EC2: Perguntas

Complementa `no-docker-change-questions.md` (suas respostas la continuam valendo: Q1 A, Q2 A, Q3 A; Q4 a Q6 seguem em aberto). **Este arquivo substitui as Q7, Q8 e Q9 daquele arquivo** (observabilidade em um EC2), porque a topologia agora esta definida.

Cada pergunta traz uma **Sugestao** da IA; para aceitar, copie a letra para `[Answer]:`. Se aceitar tudo, responda "aceito todas as sugestoes".

## Restricao informada pelo usuario (2026-10-07)

**Tres maquinas EC2**: (1) **observabilidade**: Prometheus, Grafana, Pushgateway e Jaeger; (2) **Elasticsearch**; (3) **a aplicacao**.

## Impacto no que ja foi decidido

| Item anterior | Efeito |
|---|---|
| Compute da aplicacao em ECS Fargate (SDD, requisitos D2 e FR-8.1) | **Substituido por EC2**: servicos como processos JVM, sem containers (consistente com a ausencia de Docker) |
| Observabilidade sem Pushgateway (requisito D13) | **Pushgateway volta ao escopo**; Alertmanager continua fora |
| Prometheus, Grafana e Jaeger no Compose local | Passam a ser **remotos** (EC2 de observabilidade); local sem stack (evidencia via Actuator e k6) |
| Elasticsearch | **Componente novo**; papel definido em T4 |
| Dispatcher em Lambda | Mantido ou nao, conforme T3 |
| `infrastructure-design.md` e `deployment-architecture.md` de U1 | Serao reescritos apos as respostas |

---

### T1: Como a aplicacao roda no EC2 de aplicacao?
A) **Servicos como processos JVM gerenciados por systemd** (jars Java 25: `ingestion-api`, `worker`, `mock-legacy`), numa unica instancia EC2; **sem ECS/Fargate e sem containers**; configuracao por variaveis de ambiente (`EnvironmentFile`), usuario de sistema nao-root, uma unit por servico
B) Cada servico em sua propria instancia EC2
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Atende "uma maquina para a aplicacao" e e o equivalente direto, sem Docker, do que o Fargate faria. As `systemd units` entram no guia de implantacao e no Terraform (U6). Risco registrado: 10.000 TPS em **uma** instancia depende do tamanho dela e nao ha escala horizontal; o limiar real sera medido pelo k6 e documentado.

[Answer]:

### T2: Borda na frente do EC2 de aplicacao
A) **ALB + WAF v2** na frente (como no SDD), definidos em Terraform e apenas validados (`validate`/`plan`); regras por IP e por `X-Tenant-ID`; ALB em subnets publicas e EC2 de aplicacao em subnet privada
B) Sem ALB/WAF nesta fase: acesso direto ao EC2 de aplicacao; rate limit somente na aplicacao
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Mantem a tese multi-tenant do SDD (WAF e limiter na aplicacao em camadas) e custa apenas Terraform; os requisitos ja previam Terraform completo sem deploy.

[Answer]:

### T3: Dados e mensageria na AWS (DynamoDB, SQS, Dispatcher)
A) **Servicos gerenciados reais**: DynamoDB (Streams, TTL), SQS (fast, standard, DLQ), **Lambda Dispatcher** e KMS, acessados pelo EC2 de aplicacao; o ambiente local usa emuladores embarcados na JVM
B) Tudo **dentro do EC2 de aplicacao**, em memoria na JVM (DynamoDB Local e ElasticMQ embarcados, Dispatcher como `local-runner` in-process), tambem na AWS
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** O DurableInbox precisa de **durabilidade real**: DynamoDB e SQS em memoria perderiam propostas aceitas se o processo reiniciasse, contrariando a tese da PoC. "Banco em memoria" fica restrito ao relacional (Q5 do arquivo anterior). Mantem a Lambda; no ambiente local o `local-runner` executa o mesmo handler.

[Answer]:

### T4: Papel do Elasticsearch (EC2 proprio)
A) **Logs e traces**: logs estruturados ECS (JSON) do EC2 de aplicacao enviados ao Elasticsearch (por Filebeat), e **backend de armazenamento de traces do Jaeger**; Kibana opcional
B) Somente logs (Filebeat para Elasticsearch)
C) Somente armazenamento de traces do Jaeger
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** O formato ECS que o `platform-starter` ja produz foi pensado para Elasticsearch, e o Jaeger precisa de storage persistente fora de memoria. A PoC entrega o **contrato** (formato, indices sugeridos, variaveis); instalar Elasticsearch e Filebeat fica com voce.

[Answer]:

### T5: Quem instala e opera as 3 EC2?
A) **Voce instala e opera** as tres maquinas; a PoC entrega Terraform apenas dos **recursos gerenciados e de rede** (DynamoDB, SQS, Lambda, KMS, ALB/WAF, security groups, IAM) e um **guia de implantacao** (units systemd, portas, variaveis, configuracoes de exemplo de Prometheus, Filebeat e Jaeger)
B) Terraform tambem cria as 3 EC2 (com user-data para instalacao)
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Voce ja definiu que instalara as ferramentas; user-data nao pode ser testado aqui (sem deploy e sem Docker). O guia permite reproduzir sem retrabalho.

[Answer]:

### T6: Coleta de metricas, traces e logs (com a topologia estatica)
A) **Metricas**: Prometheus (EC2 de observabilidade) faz **scrape** de `/actuator/prometheus` do EC2 de aplicacao (IP privado fixo, porta restrita por security group); **Pushgateway** recebe metricas da **Lambda Dispatcher** (curta duracao) e de jobs; **traces**: OTLP direto ao Jaeger; **logs**: Filebeat para Elasticsearch
B) Tudo por push via OTLP para um OpenTelemetry Collector no EC2 de observabilidade
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Com instancia de IP fixo o scrape e simples e dispensa descoberta de servico; justifica o Pushgateway que voce listou no unico caso em que faz sentido (Lambda). Cuidado documentado: metricas no Pushgateway nao expiram sozinhas (limpeza por job/instancia).

[Answer]:

### T7: Seguranca de rede entre as 3 maquinas e acessos administrativos
A) VPC privada; **security groups minimos por papel** (observabilidade para aplicacao em 8080 para scrape; aplicacao e Lambda para observabilidade em OTLP 4317/4318 e Pushgateway 9091; aplicacao para Elasticsearch em 9200 apenas pelo Filebeat; Jaeger para Elasticsearch em 9200); **sem IP publico** em observabilidade e Elasticsearch; ALB publico so em 443; administracao por **SSM Session Manager**; Elasticsearch com seguranca habilitada (usuario/senha ou API key em Secrets Manager); TLS no OTLP e no Elasticsearch registrado como melhoria e risco
B) Acesso publico com TLS e autenticacao em todos os componentes
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Atende SECURITY-06, 07 e 08 sem expor dashboards nem o Elasticsearch de um sistema financeiro. Os security groups entram no Terraform (U6).

[Answer]:

---

## Riscos e premissas registrados
1. 10.000 TPS em uma unica instancia de aplicacao: depende do tamanho da instancia; sem escala horizontal. Medir e documentar.
2. A carga do k6 deve vir de fora da maquina da aplicacao para ser representativa; localmente os numeros sao indicativos.
3. Pushgateway: metricas persistem ate serem removidas; uso restrito a Lambda e jobs.
4. TLS no OTLP e no Elasticsearch nao e coberto nesta fase.
5. Nada sera implantado na AWS nesta entrega (requisito D2 mantido).

---

## Complemento: custo do NAT Gateway (restricao informada em 2026-10-07)

> "o problema na aws e o natgateway que fica cobrando mesmo sem uso e eu acho que nao da para desligar ele."

Correto: o NAT Gateway e cobrado **por hora enquanto existir** (mais o volume de dados processado) e nao tem "pausa"; so e possivel **destrui-lo e recria-lo**. Ordem de grandeza (aproximada, varia por regiao e muda com o tempo; `sa-east-1` costuma ser mais cara; confirme na calculadora da AWS): NAT Gateway na faixa de dezenas de dolares por mes por AZ, contra cerca de poucos dolares por mes por IPv4 publico. Outros recursos tambem cobram por hora enquanto existirem: **ALB, WAF (web ACL e regras), instancias EC2, IPv4 publicos e VPC endpoints de interface** (SQS, KMS, SSM etc.; os endpoints de **gateway** para DynamoDB e S3 sao gratuitos).

### Efeito no desenho sugerido
- A sugestao de **T7** ("sem IP publico" nas EC2 de observabilidade e Elasticsearch, o que exige NAT ou endpoints para saida) **fica substituida por T8** abaixo.
- A regra SECURITY-07 continua valida: sem entrada `0.0.0.0/0` exceto ALB 80/443; saida restrita; sub-redes privadas nao podem ter rota direta para o internet gateway. Sub-redes **publicas** com security groups fechados sao permitidas.
- Como esta entrega **nao implanta nada** (somente `terraform validate/plan`), o custo so existe quando voce der `apply`. O desenho abaixo decide o que sera cobrado nessa hora.

### T8: Saida para a internet e acesso a servicos AWS sem NAT Gateway
A) **Sem NAT Gateway e sem VPC endpoints de interface**: as 3 EC2 ficam em **sub-redes publicas** com IPv4 publico, mas com **security groups fechados** (nenhuma entrada de `0.0.0.0/0`; entradas somente entre os security groups das proprias maquinas e, no caso do ALB, 443); **saida restrita a 443** (e as portas internas necessarias); **VPC endpoint de gateway (gratuito) para DynamoDB**; SQS, KMS, SSM e Secrets Manager acessados pelos enderecos publicos da AWS via internet gateway (TLS); administracao por **SSM Session Manager** (sem SSH aberto); Terraform com variavel **`enable_nat_gateway = false`** (padrao), para ligar apenas se um dia sub-redes privadas forem necessarias
B) **NAT instance** (EC2 pequena com encaminhamento de pacotes) em vez de NAT Gateway: pode ser **parada** quando nao usada; menor custo; exige manutencao manual e nao escala
C) NAT Gateway criado e destruido **sob demanda** via Terraform (`apply`/`destroy` durante testes ou demos); EC2 em sub-redes privadas; custo somente enquanto estiver no ar
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Zera o custo de NAT e de endpoints de interface, mantem o fluxo de rede simples e atende SECURITY-07 com security groups restritivos. O custo remanescente e o IPv4 publico por instancia (pequeno). Riscos registrados: superficie de rede maior que em sub-rede privada (mitigada por security groups e SSM, sem SSH); o trafego para SQS/KMS sai pelo internet gateway (TLS), nao por endpoint privado. A opcao C e boa se voce prefere sub-redes privadas e aceita recriar o NAT a cada sessao.

[Answer]:

### T9: Onde roda a Lambda do Dispatcher (afeta custo e o uso do Pushgateway)
Uma Lambda **fora da VPC** acessa DynamoDB e SQS pela rede da AWS sem NAT nem endpoints, mas **nao alcanca** maquinas em sub-rede privada nem metricas por Pushgateway restrito. Uma Lambda **dentro da VPC** precisa de NAT ou endpoints para chamar as APIs da AWS (custo).

A) **Lambda fora da VPC**: so chama DynamoDB (Streams) e SQS; **metricas do Dispatcher via CloudWatch (Embedded Metric Format) e logs no CloudWatch Logs**, sem Pushgateway; o **Pushgateway fica opcional** (jobs de curta duracao como o k6, se voce quiser)
B) Lambda **dentro da VPC** com NAT ou endpoints (custo por hora) para empurrar metricas ao Pushgateway
C) **Sem Lambda**: o Dispatcher roda como **servico systemd na EC2 da aplicacao** (poller de DynamoDB Streams, mesmo handler), sem custo de Lambda/VPC e com metricas por scrape; exige tratar shards do Stream no codigo
D) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Evita NAT e endpoints, mantem a Lambda gerenciada (retentativa e escala nativas do Streams) e nao expoe o Pushgateway. Contrapartida: as metricas do Dispatcher ficam no CloudWatch, nao no Prometheus (o dashboard de backlog usa as metricas do Worker e das filas, que continuam no Prometheus). A opcao C e a mais barata e a mais simples de rede, mas troca robustez por codigo proprio de leitura de shards.

[Answer]:

## Riscos e premissas adicionais
6. Recursos que cobram por hora enquanto existirem (EC2, ALB, WAF, IPv4 publico): destruir com `terraform destroy` apos testes; nada e criado nesta entrega.
7. Estimativas de custo acima sao ordens de grandeza; validar na calculadora da AWS para a regiao escolhida (Q17 dos requisitos: `sa-east-1` ou `us-east-1`; `us-east-1` costuma ser mais barata).

---

## Revisao: ambiente efemero (apply e destroy a cada uso)

> "mas nos podemos criar o terraform, e destruir tudo depois de usar"  (usuario, 2026-10-07)

Com `terraform apply` para subir e `terraform destroy` para derrubar tudo, o NAT Gateway (e ALB, WAF, EC2, IPv4 publico) so cobra **enquanto o ambiente estiver de pe**: uma sessao de poucas horas custa uma fracao do mensal. O risco passa a ser **esquecer de destruir** (NAT Gateway na faixa de dezenas de dolares por mes por AZ; ordem de grandeza nao verificada).

### O que muda nas sugestoes anteriores
| Item | Revisao |
|---|---|
| **T8** (saida sem NAT) | A **sugestao passa a ser C**: sub-redes privadas + NAT Gateway **dentro do stack efemero**, destruido com o resto. Mantem as EC2 sem IP publico (SECURITY-07), o que restaura a sugestao original de **T7-A**. A opcao A (sem NAT) continua valida se voce preferir custo zero de NAT mesmo deixando o ambiente ligado |
| **T5** (quem instala as EC2) | **Instalacoes manuais se perdem a cada `destroy`.** A sugestao deixa de ser "voce instala": ver T5 revisada abaixo |
| **T7** | Volta a valer a sugestao original (sem IP publico em observabilidade e Elasticsearch) |
| **Escopo "sem deploy" (requisito D2)** | Continua: **eu nao executo `apply` nem `destroy`**; o Terraform deve ficar **pronto para voce aplicar e destruir** (ver T11) |
| **T9** (Lambda) | Sugestao mantida (A: fora da VPC); com NAT no stack a Lambda em VPC seria possivel, mas nao e necessaria |
| **T2** (ALB + WAF) | Sugestao mantida (A); o custo por hora so existe durante a sessao |

### T5 revisada: como as 3 EC2 recebem as ferramentas a cada `apply`?
A) **AMIs proprias**: voce prepara cada maquina **uma vez**, cria uma AMI (guarda so o custo de armazenamento do snapshot) e o Terraform sobe as instancias a partir de variaveis `*_ami_id`; a PoC entrega o guia de instalacao
B) **Instalacao automatica por user-data** (scripts versionados para Prometheus, Grafana, Pushgateway, Jaeger, Elasticsearch/Filebeat e units systemd da aplicacao), em AMI padrao do Amazon Linux, a cada `apply`
C) **Ambos**: scripts de user-data como padrao e variavel `*_ami_id` opcional para usar AMIs ja preparadas (mais rapido e barato em tempo)
D) Other (please describe after [Answer]: tag below)

**Sugestao: C.** O ciclo apply/destroy torna a instalacao manual repetitiva; user-data reproduz tudo do zero e a AMI opcional acelera as sessoes. **Ressalva:** nao tenho como executar os scripts aqui (sem deploy e sem Docker); eles serao revisados e validados por sintaxe e so se provam no primeiro `apply` (voce me traz os erros). Se preferir menos escopo, A e a mais enxuta.

[Answer]:

### T10: Posso usar suas credenciais AWS para `terraform plan`?
`terraform validate` roda offline; `terraform plan` precisa de credenciais AWS (somente leitura de estado/recursos existentes, **sem criar nada**).

A) **Nao**: somente `terraform init`/`validate` e analise estatica; voce roda `plan` e `apply`
B) **Sim para `plan`**, com um perfil AWS que voce indicar; **nunca** `apply` ou `destroy`
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** Nao uso credenciais da sua conta sem necessidade; o risco de erro de `plan` que so aparece com a conta e tratado quando voce rodar o primeiro `plan`.

[Answer]:

### T11: Terraform destrutivel e protecao contra custo esquecido
A) **Tudo destrutivel por padrao** (sem `deletion_protection`, buckets de logs com `force_destroy`, segredos com `recovery_window_in_days = 0`, log groups gerenciados pelo Terraform), mais **AWS Budgets** com alerta de custo por e-mail no proprio Terraform, e um **guia de uso** (`apply` -> testar -> `destroy`; como confirmar que nao restou recurso cobrando)
B) Recursos de dados (DynamoDB, logs) com protecao e retencao; so computacao efemera
C) Other (please describe after [Answer]: tag below)

**Sugestao: A.** E o que seu fluxo exige. Observacao: chaves KMS nao somem na hora (janela minima de 7 dias antes da exclusao definitiva), o que e esperado e documentado; AWS Budgets tem camada gratuita para poucos orcamentos (confirmar nas condicoes atuais).

[Answer]:

---

## Revisao 2: modo liga/desliga (EC2 parada + NAT removido) em vez de destroy completo

> "os ec2 da para desligar e nao cobrar. o problema aqui e o natgateway, da para desligar os ec2 e dar um destroy com o net e depois contruir ele novamente?" (usuario, 2026-10-07)

**Sim.** EC2 **parada** nao cobra computacao (continua o EBS); o NAT Gateway pode ser removido e recriado por Terraform. Os discos mantem o software instalado e os dados.

### Desenho proposto (variavel unica `environment_on`)
| Recurso | `environment_on = true` | `environment_on = false` |
|---|---|---|
| NAT Gateway + IP elastico + rota privada | existe | **removido** |
| ALB (+ associacao do WAF) | existe | **removido** (ALB cobra por hora e nao pode ser parado) |
| 3 EC2 | em execucao (`aws_ec2_instance_state = running`) | **paradas** (`stopped`) |
| VPC, sub-redes, security groups, EBS, DynamoDB sob demanda, SQS, Lambda, KMS | existem | existem (custo ocioso baixo ou zero; EBS e KMS cobram valores pequenos) |
| `terraform destroy` | remove tudo (incluindo EBS: software e dados se perdem) | idem |

### Ordem obrigatoria
- **Ligar**: `apply` com `environment_on=true` (cria NAT e ALB) e depois iniciar as EC2 (o mesmo `apply` pode fazer ambos, com dependencia de ordem).
- **Desligar**: parar as EC2 e so entao `apply` com `environment_on=false`.
- Motivo: sem NAT a aplicacao nao alcanca SQS/KMS e o SSM Session Manager deixa de funcionar; o NAT precisa existir sempre que a aplicacao estiver rodando.

### Efeito nas perguntas
| Pergunta | Sugestao revisada |
|---|---|
| **T8** | **C com o modo liga/desliga acima**: sub-redes privadas, NAT Gateway e ALB sob `environment_on`. Continua valida a opcao A (sem NAT) para quem prefira custo zero de NAT sem disciplina de desligar |
| **T5** | **A**: voce instala cada maquina **uma vez**; como as EC2 so **param**, o software e os dados persistem no EBS. Scripts de user-data (opcao B/C) e AMIs so valem a pena se voce fizer `terraform destroy` completo com frequencia |
| **T11** | **A**, ampliada: alem de tudo destrutivel por `destroy`, o guia de uso descreve `ligar` e `desligar` (comandos e ordem) e como confirmar que nao restou recurso cobrando (NAT, ALB, EBS, KMS) |

### Riscos
- EBS (e snapshots/AMIs, se houver) e KMS continuam cobrando valores pequenos no modo desligado (nao verificados; confirme na calculadora da AWS).
- Recriar NAT/ALB leva alguns minutos; o NAT volta com **novo IP publico** (irrelevante aqui, pois nao ha allowlist por IP de saida).
- Se `terraform apply` for interrompido entre desligar EC2 e remover NAT, o ambiente fica parcialmente ligado; rodar `apply` de novo converge.
