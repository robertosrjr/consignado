param([string]$Profile = 'default', [string]$PublishPolicyName = '')
$ErrorActionPreference = 'Stop'
$region = 'sa-east-1'
$topicArn = 'arn:aws:sns:sa-east-1:247755251743:alert-consignado'
$queueArn = 'arn:aws:sqs:sa-east-1:247755251743:alert-consignado-sqs'
$queueUrl = 'https://sqs.sa-east-1.amazonaws.com/247755251743/alert-consignado-sqs'
$instanceId = 'i-082b5f183d18a8d25'
function Read-Aws([string[]]$Arguments) {
    $result = & aws @Arguments --profile $Profile --region $region --output json --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw "Consulta AWS falhou: $($Arguments[0..1] -join ' '). Renove aws login se necessario." }
    return ($result | ConvertFrom-Json)
}
function Write-Utf8([string]$Name, [string]$Text) {
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot $Name),$Text,[Text.UTF8Encoding]::new($false))
}
$identity = Read-Aws -Arguments @('sts','get-caller-identity')
if ($identity.Account -ne '247755251743') { throw 'Conta AWS diferente da conta autorizada.' }
$subscriptions = Read-Aws -Arguments @('sns','list-subscriptions-by-topic','--topic-arn',$topicArn)
$matchingSubscriptions = @($subscriptions.Subscriptions | Where-Object { $_.Protocol -eq 'sqs' -and $_.Endpoint -eq $queueArn })
if ($matchingSubscriptions.Count -ne 1 -or $matchingSubscriptions[0].SubscriptionArn -notlike 'arn:aws:sns:*') {
    throw 'Esperada exatamente uma subscription SQS confirmada para o topico/fila.'
}
$subscriptionArn = $matchingSubscriptions[0].SubscriptionArn
$subscription = Read-Aws -Arguments @('sns','get-subscription-attributes','--subscription-arn',$subscriptionArn)
$topic = Read-Aws -Arguments @('sns','get-topic-attributes','--topic-arn',$topicArn)
$queue = Read-Aws -Arguments @('sqs','get-queue-attributes','--queue-url',$queueUrl,'--attribute-names','All')
if ($queue.Attributes.QueueArn -ne $queueArn) { throw 'ARN inesperado na fila.' }
$associations = Read-Aws -Arguments @('ec2','describe-iam-instance-profile-associations','--filters',"Name=instance-id,Values=$instanceId")
$association = @($associations.IamInstanceProfileAssociations | Where-Object State -eq 'associated')
if ($association.Count -ne 1) { throw 'EC2 sem exatamente um instance profile associado.' }
$profileName = ($association[0].IamInstanceProfile.Arn -split '/')[-1]
$instanceProfile = Read-Aws -Arguments @('iam','get-instance-profile','--instance-profile-name',$profileName)
$instanceResult = Read-Aws -Arguments @('ec2','describe-instances','--instance-ids',$instanceId)
$instance = $instanceResult.Reservations[0].Instances[0]
$roles = @($instanceProfile.InstanceProfile.Roles)
if ($roles.Count -ne 1) { throw 'Instance profile sem role unico.' }
$roleName = $roles[0].RoleName
$role = Read-Aws -Arguments @('iam','get-role','--role-name',$roleName)
if (-not $PublishPolicyName) {
    $policies = Read-Aws -Arguments @('iam','list-role-policies','--role-name',$roleName)
    $candidates = @()
    foreach ($name in $policies.PolicyNames) {
        $policy = Read-Aws -Arguments @('iam','get-role-policy','--role-name',$roleName,'--policy-name',$name)
        $statements = @($policy.PolicyDocument.Statement)
        if ($statements.Count -eq 1 -and $statements[0].Effect -eq 'Allow' -and
            @($statements[0].Action).Count -eq 1 -and $statements[0].Action -eq 'sns:Publish' -and
            $statements[0].Resource -eq $topicArn -and -not $statements[0].Condition) { $candidates += $name }
    }
    if ($candidates.Count -ne 1) { throw 'Nao foi encontrada uma unica inline policy exclusiva de SNS. Informe -PublishPolicyName e revise o plano.' }
    $PublishPolicyName = $candidates[0]
}
$basePolicy = @{Version='2012-10-17';Statement=@()}
if ($queue.Attributes.Policy) { $basePolicy = $queue.Attributes.Policy | ConvertFrom-Json; $basePolicy.Statement = @($basePolicy.Statement) }
$values = @{
    instance_id=$instanceId; role_name=$roleName; role_path=$role.Role.Path
    instance_profile_name=$profileName; instance_profile_path=$instanceProfile.InstanceProfile.Path
    publish_policy_name=$PublishPolicyName
    assume_role_policy_json=($role.Role.AssumeRolePolicyDocument | ConvertTo-Json -Depth 30 -Compress)
    existing_queue_policy_json=($basePolicy | ConvertTo-Json -Depth 30 -Compress)
    raw_message_delivery=($subscription.Attributes.RawMessageDelivery -eq 'true')
    visibility_timeout_seconds=[int]$queue.Attributes.VisibilityTimeout
    message_retention_seconds=[int]$queue.Attributes.MessageRetentionPeriod
    receive_wait_time_seconds=[int]$queue.Attributes.ReceiveMessageWaitTimeSeconds
    max_message_size=[int]$queue.Attributes.MaximumMessageSize
    delay_seconds=[int]$queue.Attributes.DelaySeconds
    sqs_managed_sse_enabled=($queue.Attributes.SqsManagedSseEnabled -eq 'true')
    ec2_config=@{
        ami=$instance.ImageId; instance_type=$instance.InstanceType; subnet_id=$instance.SubnetId
        security_group_ids=@($instance.SecurityGroups | ForEach-Object GroupId)
        key_name=$instance.KeyName; associate_public_ip_address=[bool]$instance.PublicIpAddress
        source_dest_check=[bool]$instance.SourceDestCheck; ebs_optimized=[bool]$instance.EbsOptimized
        monitoring=($instance.Monitoring.State -eq 'enabled')
        tags=@{}; http_endpoint=$instance.MetadataOptions.HttpEndpoint
        http_tokens=$instance.MetadataOptions.HttpTokens
        http_put_response_hop_limit=[int]$instance.MetadataOptions.HttpPutResponseHopLimit
        http_protocol_ipv6=$instance.MetadataOptions.HttpProtocolIpv6
        instance_metadata_tags=$instance.MetadataOptions.InstanceMetadataTags
    }
}
foreach ($tag in $instance.Tags) { $values.ec2_config.tags[$tag.Key]=$tag.Value }
foreach ($pair in @(@('kms_master_key_id',$queue.Attributes.KmsMasterKeyId),@('redrive_policy',$queue.Attributes.RedrivePolicy),@('sns_kms_master_key_id',$topic.Attributes.KmsMasterKeyId),@('subscription_filter_policy',$subscription.Attributes.FilterPolicy),@('subscription_filter_policy_scope',$subscription.Attributes.FilterPolicyScope),@('subscription_redrive_policy',$subscription.Attributes.RedrivePolicy))) {
    if ($pair[1]) { $values[$pair[0]]=$pair[1] }
}
if ($queue.Attributes.KmsDataKeyReusePeriodSeconds) { $values.kms_data_key_reuse_period_seconds=[int]$queue.Attributes.KmsDataKeyReusePeriodSeconds }
$imports = [ordered]@{
    'aws_sns_topic.alerts'=$topicArn
    'aws_sqs_queue.alerts'=$queueUrl
    'aws_sns_topic_subscription.alerts_queue'=$subscriptionArn
    'aws_iam_role.alertmanager'=$roleName
    'aws_iam_role_policy.sns_publish'="${roleName}:$PublishPolicyName"
    'aws_iam_instance_profile.alertmanager'=$profileName
    'aws_instance.observability'=$instanceId
}
if ($queue.Attributes.Policy) { $imports['aws_sqs_queue_policy.sns_delivery']=$queueUrl }
$blocks = foreach ($entry in $imports.GetEnumerator()) {
    "import {`n  to = $($entry.Key)`n  id = $($entry.Value | ConvertTo-Json -Compress)`n}`n"
}
Write-Utf8 'terraform.tfvars.json' ($values | ConvertTo-Json -Depth 30)
Write-Utf8 'imports.generated.tf' ($blocks -join "`n")
Write-Utf8 'inventory.json' (@{queue=$queue;topic=$topic;subscription=$subscription;association=$association;role=$role;profile=$instanceProfile} | ConvertTo-Json -Depth 40)
Write-Output 'Inventario e imports gerados. Nenhum recurso AWS foi alterado.'
Write-Output 'Execute terraform plan -out=alerting.tfplan e revise imports/alteracoes antes de aplicar.'
