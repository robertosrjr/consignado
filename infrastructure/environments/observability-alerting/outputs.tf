output "topic_arn" {
  value = aws_sns_topic.alerts.arn
}

output "queue_arn" {
  value = aws_sqs_queue.alerts.arn
}

output "queue_url" {
  value = aws_sqs_queue.alerts.url
}

output "subscription_arn" {
  value = aws_sns_topic_subscription.alerts_queue.arn
}

output "instance_profile_name" {
  value = aws_iam_instance_profile.alertmanager.name
}
