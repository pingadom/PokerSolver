output "api_endpoint" { value = "${var.certificate_arn == "" ? "http" : "https"}://${aws_lb.api.dns_name}" }
output "queue_url" { value = aws_sqs_queue.main.url }
output "queue_name" { value = aws_sqs_queue.main.name }
output "dead_letter_queue_url" { value = aws_sqs_queue.dead.url }
output "cluster_name" { value = aws_ecs_cluster.main.name }
output "repository_urls" { value = { for name, repo in aws_ecr_repository.service : name => repo.repository_url } }
output "service_names" { value = { for name, service in aws_ecs_service.service : name => service.name } }
output "artifact_bucket" { value = aws_s3_bucket.artifacts.id }
