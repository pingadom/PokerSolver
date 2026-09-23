locals {
  services = { api = var.api_count, worker = var.worker_count }
  environment = [
    { name = "DATABASE_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/pokerlab?sslmode=require" },
    { name = "DATABASE_USER", value = "pokerlab" },
    { name = "QUEUE_MODE", value = "sqs" },
    { name = "AWS_REGION", value = var.region },
    { name = "SQS_QUEUE_URL", value = aws_sqs_queue.main.url },
    { name = "SQS_DLQ_URL", value = aws_sqs_queue.dead.url },
    { name = "PORT", value = "8080" },
    { name = "CACHE_ENABLED", value = tostring(var.enable_redis) },
    { name = "REDIS_HOST", value = var.enable_redis ? aws_elasticache_replication_group.main[0].primary_endpoint_address : "localhost" },
    { name = "SPRING_DATA_REDIS_SSL_ENABLED", value = tostring(var.enable_redis) }
  ]
}
resource "aws_ecr_repository" "service" {
  for_each             = local.services
  name                 = "${var.name}-${each.key}"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
}
resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = local.services
  repository = aws_ecr_repository.service[each.key].name
  policy     = jsonencode({ rules = [{ rulePriority = 1, description = "Keep last ten images", selection = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }, action = { type = "expire" } }] })
}
resource "aws_cloudwatch_log_group" "service" {
  for_each          = local.services
  name              = "/ecs/${var.name}/${each.key}"
  retention_in_days = 7
}
resource "aws_ecs_cluster" "main" {
  name = var.name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}
resource "aws_iam_role" "execution" {
  name_prefix        = "${var.name}-execution-"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }] })
}
resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}
resource "aws_iam_role_policy" "secret" {
  role   = aws_iam_role.execution.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = [aws_db_instance.main.master_user_secret[0].secret_arn] }] })
}
resource "aws_iam_role" "task" {
  for_each           = local.services
  name_prefix        = "${var.name}-${each.key}-"
  assume_role_policy = aws_iam_role.execution.assume_role_policy
}
resource "aws_iam_role_policy" "queue" {
  for_each = local.services
  role     = aws_iam_role.task[each.key].id
  policy   = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = each.key == "api" ? ["sqs:SendMessage", "sqs:GetQueueAttributes"] : ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes"], Resource = each.key == "api" ? [aws_sqs_queue.main.arn] : [aws_sqs_queue.main.arn, aws_sqs_queue.dead.arn] }] })
}
resource "aws_ecs_task_definition" "service" {
  for_each                 = local.services
  family                   = "${var.name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task[each.key].arn
  container_definitions = jsonencode([{
    name             = each.key, image = "${aws_ecr_repository.service[each.key].repository_url}:${var.image_tag}", essential = true,
    portMappings     = [{ containerPort = 8080, protocol = "tcp" }],
    environment      = local.environment,
    secrets          = [{ name = "DATABASE_PASSWORD", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::" }],
    logConfiguration = { logDriver = "awslogs", options = { awslogs-group = aws_cloudwatch_log_group.service[each.key].name, awslogs-region = var.region, awslogs-stream-prefix = "ecs" } },
    healthCheck      = { command = ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1"], interval = 30, timeout = 5, retries = 3, startPeriod = 60 },
    stopTimeout      = 120
  }])
}
resource "aws_ecs_service" "service" {
  for_each        = local.services
  name            = "${var.name}-${each.key}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  desired_count   = each.value
  launch_type     = "FARGATE"
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  network_configuration {
    subnets          = aws_subnet.public[*].id
    assign_public_ip = true
    security_groups  = [each.key == "api" ? aws_security_group.api.id : aws_security_group.worker.id]
  }
  dynamic "load_balancer" {
    for_each = each.key == "api" ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.api.arn
      container_name   = "api"
      container_port   = 8080
    }
  }
  depends_on = [aws_lb_listener.http, aws_lb_listener.https, aws_iam_role_policy.secret, aws_iam_role_policy_attachment.execution, aws_iam_role_policy.queue]
}
resource "aws_lb" "api" {
  name                       = var.name
  load_balancer_type         = "application"
  subnets                    = aws_subnet.public[*].id
  security_groups            = [aws_security_group.alb.id]
  drop_invalid_header_fields = true
}
resource "aws_lb_target_group" "api" {
  name        = var.name
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id
  health_check {
    path    = "/actuator/health"
    matcher = "200"
  }
}
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type             = var.certificate_arn == "" ? "forward" : "redirect"
    target_group_arn = var.certificate_arn == "" ? aws_lb_target_group.api.arn : null
    dynamic "redirect" {
      for_each = var.certificate_arn == "" ? [] : [1]
      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}
resource "aws_lb_listener" "https" {
  count             = var.certificate_arn == "" ? 0 : 1
  load_balancer_arn = aws_lb.api.arn
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = var.certificate_arn
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}
