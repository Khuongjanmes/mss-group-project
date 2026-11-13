# ECS cluster trên Fargate: mỗi thành phần (8 service + web) là một ECS Service riêng,
# có task definition, số task, log và cách deploy riêng.

resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled" # CPU/RAM từng service trên CloudWatch
  }
}

# ---- IAM ----

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# Execution role: ECS dùng để ghi log và đọc secret lúc khởi động task.
resource "aws_iam_role" "execution" {
  name               = "${local.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secrets" {
  name = "read-app-secret"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [aws_secretsmanager_secret.app.arn]
    }]
  })
}

# Task role: quyền của chính ứng dụng. Chỉ cần cho ECS Exec (aws ecs execute-command) để vào container gỡ lỗi.
resource "aws_iam_role" "task" {
  name               = "${local.name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy" "task_exec" {
  name = "ecs-exec"
  role = aws_iam_role.task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "ssmmessages:CreateControlChannel",
        "ssmmessages:CreateDataChannel",
        "ssmmessages:OpenControlChannel",
        "ssmmessages:OpenDataChannel",
      ]
      Resource = "*"
    }]
  })
}

# ---- Cloud Map: tên DNS nội bộ eureka.openevent.local trỏ tới task Eureka ----

resource "aws_service_discovery_private_dns_namespace" "main" {
  name = "${local.name}.local"
  vpc  = aws_vpc.main.id
}

resource "aws_service_discovery_service" "eureka" {
  name = "eureka"

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.main.id
    routing_policy = "MULTIVALUE"
    dns_records {
      type = "A"
      ttl  = 10
    }
  }
}

# ---- Định nghĩa từng service ----

locals {
  eureka_url = "http://eureka.${aws_service_discovery_private_dns_namespace.main.name}:8761/eureka"

  java_env = {
    EUREKA_URL = local.eureka_url
    # Task có 1 GB RAM nên nới heap so với mặc định 256m trong Dockerfile.
    JAVA_TOOL_OPTIONS = "-Xmx512m -XX:+ExitOnOutOfMemoryError"
    # Eureka đăng ký IP trong VPC của task, không lấy nhầm interface nội bộ của Fargate.
    SPRING_CLOUD_INETUTILS_PREFERREDNETWORKS = join(".", slice(split(".", var.vpc_cidr), 0, 2))
  }

  db_env = {
    DB_HOST = aws_db_instance.postgres.address
    DB_PORT = tostring(aws_db_instance.postgres.port)
    DB_USER = aws_db_instance.postgres.username
  }

  # Amazon MQ chỉ nhận AMQPS (TLS, cổng 5671).
  mq_env = {
    RABBITMQ_HOST               = local.mq_host
    RABBITMQ_PORT               = "5671"
    RABBITMQ_USER               = "openevent"
    SPRING_RABBITMQ_SSL_ENABLED = "true"
  }

  services = {
    eureka-server = {
      port    = 8761, cpu = 512, memory = 1024, count = 1, java = true
      env     = tomap({ EUREKA_HOSTNAME = "eureka.${aws_service_discovery_private_dns_namespace.main.name}" })
      secrets = tolist([])
    }
    api-gateway = {
      port    = 8080, cpu = 512, memory = 1024, count = 2, java = true
      env     = tomap({ CORS_ALLOWED_ORIGINS = local.app_url })
      secrets = ["JWT_SECRET"]
    }
    identity-service = {
      port = 8081, cpu = 512, memory = 1024, count = 1, java = true
      # Phát PasswordResetRequested (quên mật khẩu) nên cần RabbitMQ như các service khác.
      env = tomap(merge(local.db_env, local.mq_env, {
        GOOGLE_CLIENT_ID      = var.google_client_id
        BOOTSTRAP_ADMIN_EMAIL = var.bootstrap_admin_email
      }))
      secrets = ["JWT_SECRET", "DB_PASSWORD", "RABBITMQ_PASSWORD", "BOOTSTRAP_ADMIN_PASSWORD"]
    }
    event-service = {
      port    = 8082, cpu = 512, memory = 1024, count = 1, java = true
      env     = tomap(merge(local.db_env, local.mq_env, { CLOUDINARY_CLOUD_NAME = var.cloudinary_cloud_name }))
      secrets = ["DB_PASSWORD", "RABBITMQ_PASSWORD", "CLOUDINARY_API_KEY", "CLOUDINARY_API_SECRET"]
    }
    ticket-service = {
      port    = 8083, cpu = 512, memory = 1024, count = 2, java = true
      env     = tomap(merge(local.db_env, local.mq_env))
      secrets = ["DB_PASSWORD", "RABBITMQ_PASSWORD"]
    }
    order-service = {
      port    = 8084, cpu = 512, memory = 1024, count = 1, java = true
      env     = tomap(merge(local.db_env, local.mq_env))
      secrets = ["DB_PASSWORD", "RABBITMQ_PASSWORD"]
    }
    payment-service = {
      port = 8085, cpu = 512, memory = 1024, count = 1, java = true
      env = tomap(merge(local.db_env, local.mq_env, {
        FRONTEND_URL     = local.app_url
        VNPAY_RETURN_URL = "${local.app_url}/api/payments/vnpay-return"
      }))
      secrets = ["DB_PASSWORD", "RABBITMQ_PASSWORD", "PAYOS_CLIENT_ID", "PAYOS_API_KEY", "PAYOS_CHECKSUM_KEY", "VNPAY_TMN_CODE", "VNPAY_HASH_SECRET"]
    }
    notification-service = {
      port = 8086, cpu = 512, memory = 1024, count = 1, java = true
      env = tomap(merge(local.mq_env, {
        FRONTEND_URL  = local.app_url
        MAIL_HOST     = var.mail_host
        MAIL_PORT     = var.mail_port
        MAIL_USERNAME = var.mail_username
        MAIL_FROM     = var.mail_from
      }))
      secrets = ["RABBITMQ_PASSWORD", "MONGODB_URI", "MAIL_PASSWORD"]
    }
    # SPA + Caddy. Sau ALB nên Caddy chỉ phục vụ HTTP cổng 80; /api/* do ALB chuyển thẳng tới gateway.
    web = {
      port    = 80, cpu = 256, memory = 512, count = 1, java = false
      env     = tomap({ SITE_ADDRESS = ":80" })
      secrets = tolist([])
    }
  }
}

resource "aws_cloudwatch_log_group" "service" {
  for_each          = local.services
  name              = "/ecs/${local.name}/${each.key}"
  retention_in_days = 7
}

resource "aws_ecs_task_definition" "service" {
  for_each = local.services

  family                   = "${local.name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc" # mỗi task có IP riêng trong VPC
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = each.key
    image     = "${var.image_prefix}-${each.key}:${var.image_tag}"
    essential = true

    portMappings = [{ containerPort = each.value.port, protocol = "tcp" }]

    environment = [
      for k, v in merge({ for k, v in local.java_env : k => v if each.value.java }, each.value.env) :
      { name = k, value = v } if v != ""
    ]

    secrets = [
      for k in each.value.secrets :
      { name = k, valueFrom = "${aws_secretsmanager_secret.app.arn}:${k}::" }
      if contains(local.secret_keys_set, k)
    ]

    # Giống healthcheck trong docker-compose.yml: ECS thay task khi actuator báo lỗi.
    healthCheck = each.value.java ? {
      command     = ["CMD-SHELL", "wget -qO- http://127.0.0.1:${each.value.port}/actuator/health || exit 1"]
      interval    = 15
      timeout     = 5
      retries     = 5
      startPeriod = 180
    } : null

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service[each.key].name
        awslogs-region        = var.region
        awslogs-stream-prefix = each.key
      }
    }
  }])

  depends_on = [aws_secretsmanager_secret_version.app]
}

locals {
  # Service nào đứng sau ALB (gateway, web) và target group tương ứng.
  load_balanced = {
    api-gateway = aws_lb_target_group.gateway.arn
    web         = aws_lb_target_group.web.arn
  }
}

resource "aws_ecs_service" "service" {
  for_each = var.services_enabled ? local.services : {}

  name                   = each.key
  cluster                = aws_ecs_cluster.main.id
  task_definition        = aws_ecs_task_definition.service[each.key].arn
  desired_count          = each.value.count
  launch_type            = "FARGATE"
  enable_execute_command = true
  propagate_tags         = "SERVICE"

  # Task mới chạy được mới tắt task cũ; deploy hỏng thì tự quay về bản trước.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = aws_subnet.private[*].id # 2 AZ: ECS rải task đều hai bên
    security_groups  = [aws_security_group.tasks.id]
    assign_public_ip = false
  }

  dynamic "load_balancer" {
    for_each = contains(keys(local.load_balanced), each.key) ? [1] : []
    content {
      target_group_arn = local.load_balanced[each.key]
      container_name   = each.key
      container_port   = each.value.port
    }
  }

  # Spring Boot trên 0.5 vCPU khởi động khá lâu; chưa tính health check của ALB trong lúc này.
  health_check_grace_period_seconds = contains(keys(local.load_balanced), each.key) ? 240 : null

  dynamic "service_registries" {
    for_each = each.key == "eureka-server" ? [1] : []
    content {
      registry_arn = aws_service_discovery_service.eureka.arn
    }
  }

  # Số task của ticket-service do autoscaling quyết định sau khi tạo.
  lifecycle {
    ignore_changes = [desired_count]
  }

  depends_on = [aws_lb_listener.http]
}

# ---- Autoscale ticket-service (tải nặng nhất lúc mở bán vé): 2 → 4 task theo CPU ----

resource "aws_appautoscaling_target" "ticket" {
  count              = var.services_enabled ? 1 : 0
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.service["ticket-service"].name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = 2
  max_capacity       = 4
}

resource "aws_appautoscaling_policy" "ticket_cpu" {
  count              = var.services_enabled ? 1 : 0
  name               = "ticket-cpu-60"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.ticket[0].service_namespace
  resource_id        = aws_appautoscaling_target.ticket[0].resource_id
  scalable_dimension = aws_appautoscaling_target.ticket[0].scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    scale_in_cooldown  = 120
    scale_out_cooldown = 60
  }
}

# ---- Task chạy một lần: tạo 5 database trên RDS (thay infra/postgres/init-databases.sh của compose) ----

resource "aws_cloudwatch_log_group" "db_init" {
  name              = "/ecs/${local.name}/db-init"
  retention_in_days = 7
}

resource "aws_ecs_task_definition" "db_init" {
  family                   = "${local.name}-db-init"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution.arn

  container_definitions = jsonencode([{
    name       = "db-init"
    image      = "public.ecr.aws/docker/library/postgres:16-alpine"
    essential  = true
    entryPoint = ["sh", "-c"]
    # Tạo database nếu chưa có, chạy lại nhiều lần không lỗi.
    command = [join(" ", [
      "set -e;",
      "for db in identity_db event_db ticket_db order_db payment_db; do",
      "if psql -tAc \"SELECT 1 FROM pg_database WHERE datname='$db'\" | grep -q 1;",
      "then echo \"$db: da co\"; else psql -c \"CREATE DATABASE $db\"; echo \"$db: da tao\"; fi;",
      "done",
    ])]
    environment = [
      { name = "PGHOST", value = aws_db_instance.postgres.address },
      { name = "PGPORT", value = tostring(aws_db_instance.postgres.port) },
      { name = "PGUSER", value = aws_db_instance.postgres.username },
      { name = "PGDATABASE", value = aws_db_instance.postgres.db_name },
    ]
    secrets = [{ name = "PGPASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_PASSWORD::" }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.db_init.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "db-init"
      }
    }
  }])

  depends_on = [aws_secretsmanager_secret_version.app]
}
