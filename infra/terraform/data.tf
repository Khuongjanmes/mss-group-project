# Dữ liệu do AWS quản lý: RDS PostgreSQL (5 database), Amazon MQ (RabbitMQ).
# MongoDB của notification-service tự chạy trên ECS (mongodb.tf): tài khoản gói free không dùng được DocumentDB.
# Mật khẩu lấy từ terraform.tfvars (sinh bằng openssl), chỉ nằm trong state Terraform và Secrets Manager.

# ---- RDS PostgreSQL ----

resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "postgres" {
  identifier     = local.name
  engine         = "postgres"
  engine_version = "16"
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  # Database mặc định; 5 database của các service tạo bằng scripts/create-databases.sh.
  db_name  = "postgres_admin"
  username = "openevent"
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.data.id]
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  backup_retention_period = 1
  skip_final_snapshot     = true # đồ án: destroy không giữ snapshot
  deletion_protection     = false
  apply_immediately       = true
}

# ---- Amazon MQ cho RabbitMQ (chỉ nhận AMQPS cổng 5671) ----

resource "aws_mq_broker" "rabbitmq" {
  broker_name                = local.name
  engine_type                = "RabbitMQ"
  engine_version             = "3.13"
  auto_minor_version_upgrade = true            # bắt buộc với RabbitMQ 3.13
  host_instance_type         = "mq.m7g.medium" # RabbitMQ 3.13 không chạy trên t3
  deployment_mode            = "SINGLE_INSTANCE"

  publicly_accessible = false
  subnet_ids          = [aws_subnet.private[0].id]
  security_groups     = [aws_security_group.data.id]

  user {
    username = "openevent"
    password = var.mq_password
  }

  logs {
    general = true
  }
}

locals {
  mq_host = split(":", trimprefix(aws_mq_broker.rabbitmq.instances[0].endpoints[0], "amqps://"))[0]

}
