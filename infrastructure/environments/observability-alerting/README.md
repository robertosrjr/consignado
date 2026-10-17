# Alertmanager -> SNS -> SQS

Terraform independente para a integracao existente na conta 247755251743, regiao sa-east-1:

- SNS Standard `alert-consignado`;
- SQS Standard `alert-consignado-sqs`;
- policy da fila permitindo sqs:SendMessage pelo SNS, restrita ao ARN do topico e conta;
- subscription SQS, preservando raw delivery/filtros/redrive existentes;
- IAM Role, inline policy minima sns:Publish, instance profile e associacao a EC2 existente
  `i-082b5f183d18a8d25`.

Este root importa a EC2 existente para gerenciar seu instance profile; nao cria VPC e nao
instala Docker/Grafana/Prometheus. AMI, tipo, subnet, SGs, tags e metadados da EC2 sao
obtidos do inventario e precisam ser revisados antes de qualquer apply. Os arquivos de runtime
continuam em `../../Configuration/observability`. O Alertmanager usa credenciais temporarias
do role via IMDSv2. O Terraform precisa de uma sessao administrativa de provisionamento;
o role da aplicacao nao deve ganhar permissoes de administracao ou consumo SQS para isso.

## Adotar os recursos criados pelo console

**Nao aplicar sem importar.** A subscription ja existe e precisa entrar no estado.

```powershell
aws login
Set-Location C:\Developer\Workspace\java\consignado\infrastructure\environments\observability-alerting
powershell -NoProfile -ExecutionPolicy Bypass -File .\preparar-importacao.ps1
terraform init
terraform validate
terraform test
terraform plan -out=alerting.tfplan
terraform show alerting.tfplan
```

O script faz SOMENTE consultas AWS e gera `terraform.tfvars.json`, `imports.generated.tf`
e `inventory.json` locais, ignorados pelo Git. Descobre o ARN da subscription, o ID da
associacao do profile, nomes/path do role/profile e a inline policy de publicacao. Tambem
preserva a policy atual da fila e configuracoes de criptografia, retencao, visibilidade,
long polling, tamanho maximo, atraso e redrive. Usa o profile default; outro via -Profile.

Se houver mais de uma policy candidata ou uma policy compartilhada com outras permissoes,
o script para. Informar -PublishPolicyName exige revisar se o Terraform nao removera
permissoes adicionais; a policy gerenciada por este root deve ser exclusiva da publicacao.
O script requer exatamente uma subscription confirmada para o topico/fila informados.

O plano deve mostrar imports e somente alteracoes intencionais. Nao aceitar destroy/replace
de fila, topico, role, profile, associacao ou subscription. Recursos principais possuem
prevent_destroy, mas isso nao impede mudancas de policy/atributos: revisar o diff completo,
inclusive tags, permissions boundary, filtros, criptografia e configuracao especifica que
possa ter sido ajustada manualmente. Nenhum apply foi executado automaticamente.

Depois da revisao, `terraform apply alerting.tfplan` adota os recursos e aplica as mudancas
aprovadas. Os blocos import sao idempotentes. Guardar estado remoto com criptografia,
locking e controle de acesso antes de uso em equipe; nenhum bucket/backend e inventado aqui.
Nao importar o mesmo recurso para este root e outro estado simultaneamente.

## Policy da fila

A policy e montada preservando statements existentes e substituindo somente o Sid
`AllowConsignadoSns`. O novo statement concede apenas sqs:SendMessage para
sns.amazonaws.com com aws:SourceArn igual ao topico e aws:SourceAccount igual a conta.
Nao concede leitura/exclusao de mensagens a EC2 do Alertmanager.

SSE-SQS nao exige conceder acesso a uma chave KMS propria. Se SNS ou SQS usarem CMK,
as policies dessa chave precisam permitir as operacoes necessarias; este root preserva
os IDs existentes e nao assume propriedade de uma chave compartilhada ou altera sua policy.

## Mensagens e teste ponta a ponta

RawMessageDelivery=true: corpo SQS e o JSON do Alertmanager. False: corpo SQS e o envelope
SNS, cujo campo Message contem esse JSON. O inventario preserva a escolha atual. O JSON
tem schemaVersion, status e alerts[], com status individual, fingerprint, horarios,
labels e annotations. Um grupo pode misturar firing/resolved. O consumidor precisa
tratar repeticoes e correlacionar episodios; nao assumir exactly-once nem ordem global.

Para validar, consultar subscription/atributos/policy com a sessao AWS de operador,
publicar um alerta sintetico com notification=sre e test_id unico no Alertmanager, e
conferir esse test_id na fila. Sucesso de publicacao no SNS nao prova entrega no SQS.
Nao purgar a fila nem apagar mensagens reais durante testes. ReceiveMessage altera a
visibilidade temporariamente: usar timeout curto e restaurar a visibilidade de mensagens
que nao sejam do teste. Se um consumidor estiver ativo, ele pode retirar a mensagem antes
da leitura manual; conferir seus logs com o mesmo test_id.

A sessao administrativa estava expirada na preparacao, e o role da EC2 retornou
AccessDenied para consultar a fila/subscription. Por isso, o teste SNS -> SQS depende
de renovar essa sessao; nao foi declarado concluido apenas por conhecer o ARN da fila.

## Testes sem AWS

`terraform test` usa mock_provider (Terraform >=1.7): verifica preservacao de policy
existente, restricao de origem/principal, permissao minima de publicacao, endpoint da
subscription e substituicao do Sid gerenciado sem duplicacao. Nao cria recursos nem
envia mensagens. `terraform init` ainda precisa baixar o provider oficial da AWS.

Referencias: [SNS com SQS](https://docs.aws.amazon.com/sns/latest/dg/subscribe-sqs-queue-to-sns-topic.html)
e [raw delivery](https://docs.aws.amazon.com/sns/latest/dg/sns-large-payload-raw-message-delivery.html).
