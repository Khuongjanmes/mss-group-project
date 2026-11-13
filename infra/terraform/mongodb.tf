# MongoDB cho notification-service, tự chạy trên ECS Fargate (tài khoản AWS gói free không dùng được DocumentDB).
# Dữ liệu nằm trên EFS nên task chết, ECS dựng task mới vẫn còn dữ liệu.
# Service khác gọi qua mongodb.openevent.local (Cloud Map), giống tên "mongodb" khi chạy compose.

# ---- EFS ----

resource "aws_security_group" "efs" {
  name        = "${local.name}-efs"
  description = "EFS: chi nhan NFS tu ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "NFS"
    from_port       = 2049
    to_port         = 2049
    protocol        = "tcp"
    security_groups = [aws_security_group.tasks.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name}-efs" }
}

resource "aws_efs_file_system" "mongo" {
  creation_token = "${local.name}-mongo"
  encrypted      = true
  tags           = { Name = "${local.name}-mongo" }
}

resource "aws_efs_mount_target" "mongo" {
  count           = length(aws_subnet.private)
  file_system_id  = aws_efs_file_system.mongo.id
  subnet_id       = aws_subnet.private[count.index].id
  security_groups = [aws_security_group.efs.id]
}

# Image mongo chạy bằng user mongodb (uid/gid 999): access point tạo thư mục /mongo thuộc user đó.
resource "aws_efs_access_point" "mongo" {
  file_system_id = aws_efs_file_system.mongo.id

  posix_user {
    uid = 999
    gid = 999
  }

  root_directory {
    path = "/mongo"
    creation_info {
      owner_uid   = 999
      owner_gid   = 999
      permissions = "0750"
    }
  }
}

# ---- ECS service ----

resource "aws_service_discovery_service" "mongodb" {
  name = "mongodb"

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.main.id
    routing_policy = "MULTIVALUE"
    dns_records {
      type = "A"
      ttl  = 10
    }
  }
}

resource "aws_cloudwatch_log_group" "mongodb" {
  name              = "/ecs/${local.name}/mongodb"
  retention_in_days = 7
}

resource "aws_ecs_task_definition" "mongodb" {
  family                   = "${local.name}-mongodb"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  volume {
    name = "data"
    efs_volume_configuration {
      file_system_id     = aws_efs_file_system.mongo.id
      transit_encryption = "ENABLED"
      authorization_config {
        access_point_id = aws_efs_access_point.mongo.id
      }
    }
  }

  container_definitions = jsonencode([{
    name      = "mongodb"
    image     = "public.ecr.aws/docker/library/mongo:7" # bản sao Docker Hub trên ECR Public, không bị giới hạn lượt kéo
    essential = true

    portMappings = [{ containerPort = 27017, protocol = "tcp" }]
    mountPoints  = [{ sourceVolume = "data", containerPath = "/data/db" }]

    # Giới hạn cache WiredTiger theo RAM của task.
    command = ["--wiredTigerCacheSizeGB", "0.25"]

    environment = [{ name = "MONGO_INITDB_ROOT_USERNAME", value = "openevent" }]
    secrets     = [{ name = "MONGO_INITDB_ROOT_PASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:MONGO_PASSWORD::" }]

    healthCheck = {
      command     = ["CMD", "mongosh", "--quiet", "--eval", "db.adminCommand('ping').ok"]
      interval    = 15
      timeout     = 5
      retries     = 5
      startPeriod = 60
    }

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.mongodb.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "mongodb"
      }
    }
  }])

  depends_on = [aws_secretsmanager_secret_version.app]
}

resource "aws_ecs_service" "mongodb" {
  name                   = "mongodb"
  cluster                = aws_ecs_cluster.main.id
  task_definition        = aws_ecs_task_definition.mongodb.arn
  desired_count          = 1
  launch_type            = "FARGATE"
  platform_version       = "LATEST" # EFS cần platform 1.4+
  enable_execute_command = true
  propagate_tags         = "SERVICE"

  # Chỉ một mongod được mở thư mục dữ liệu: dừng task cũ rồi mới chạy task mới.
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.tasks.id]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = aws_service_discovery_service.mongodb.arn
  }

  depends_on = [aws_efs_mount_target.mongo]
}

locals {
  mongodb_uri = "mongodb://openevent:${var.mongo_password}@mongodb.${aws_service_discovery_private_dns_namespace.main.name}:27017/notification_db?authSource=admin"
}
