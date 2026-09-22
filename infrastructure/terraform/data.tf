resource "aws_sqs_queue" "dead" {
  name                      = "${var.name}-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}
resource "aws_sqs_queue" "main" {
  name                       = var.name
  visibility_timeout_seconds = 120
  receive_wait_time_seconds  = 10
  message_retention_seconds  = 345600
  sqs_managed_sse_enabled    = true
  redrive_policy             = jsonencode({ deadLetterTargetArn = aws_sqs_queue.dead.arn, maxReceiveCount = 5 })
}
resource "aws_sqs_queue_redrive_allow_policy" "dead" {
  queue_url            = aws_sqs_queue.dead.id
  redrive_allow_policy = jsonencode({ redrivePermission = "byQueue", sourceQueueArns = [aws_sqs_queue.main.arn] })
}
resource "aws_db_subnet_group" "main" {
  name       = var.name
  subnet_ids = aws_subnet.data[*].id
}
resource "aws_db_instance" "main" {
  identifier                  = var.name
  engine                      = "postgres"
  engine_version              = "16"
  instance_class              = "db.t4g.micro"
  allocated_storage           = 20
  max_allocated_storage       = 30
  storage_type                = "gp3"
  storage_encrypted           = true
  db_name                     = "pokerlab"
  username                    = "pokerlab"
  manage_master_user_password = true
  db_subnet_group_name        = aws_db_subnet_group.main.name
  vpc_security_group_ids      = [aws_security_group.database.id]
  publicly_accessible         = false
  multi_az                    = false
  backup_retention_period     = 1
  deletion_protection         = false
  skip_final_snapshot         = true
  auto_minor_version_upgrade  = true
}
resource "aws_elasticache_subnet_group" "main" {
  count      = var.enable_redis ? 1 : 0
  name       = var.name
  subnet_ids = aws_subnet.data[*].id
}
resource "aws_elasticache_replication_group" "main" {
  count                      = var.enable_redis ? 1 : 0
  replication_group_id       = var.name
  description                = "Optional PokerLab status and immutable result cache"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = "cache.t4g.micro"
  num_cache_clusters         = 1
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.main[0].name
  security_group_ids         = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
}
resource "aws_s3_bucket" "artifacts" { bucket_prefix = "${var.name}-artifacts-" }
resource "aws_s3_bucket_public_access_block" "artifacts" {
  bucket                  = aws_s3_bucket.artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_server_side_encryption_configuration" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
resource "aws_s3_bucket_lifecycle_configuration" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  rule {
    id     = "expire-demo-artifacts"
    status = "Enabled"
    filter { prefix = "" }
    expiration { days = 30 }
  }
}
