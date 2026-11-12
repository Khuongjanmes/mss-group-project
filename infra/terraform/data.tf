# Dữ liệu do AWS quản lý: RDS PostgreSQL (5 database), Amazon MQ (RabbitMQ), DocumentDB (thay MongoDB).
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
  auto_minor_version_upgrade = true # bắt buộc với RabbitMQ 3.13
  host_instance_type         = "mq.t3.micro"
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

# ---- DocumentDB (tương thích MongoDB) cho notification-service ----

resource "aws_docdb_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

# Tắt TLS để driver Mongo trong service không cần nạp chứng chỉ CA của AWS (không phải sửa image).
# Traffic vẫn nằm trong private subnet và chỉ task ECS vào được (security group "data").
resource "aws_docdb_cluster_parameter_group" "main" {
  name   = local.name
  family = "docdb5.0"

  parameter {
    name         = "tls"
    value        = "disabled"
    apply_method = "pending-reboot"
  }
}

resource "aws_docdb_cluster" "main" {
  cluster_identifier              = local.name
  engine                          = "docdb"
  engine_version                  = "5.0.0"
  master_username                 = "openevent"
  master_password                 = var.docdb_password
  db_subnet_group_name            = aws_docdb_subnet_group.main.name
  vpc_security_group_ids          = [aws_security_group.data.id]
  db_cluster_parameter_group_name = aws_docdb_cluster_parameter_group.main.name
  storage_encrypted               = true
  skip_final_snapshot             = true
  deletion_protection             = false
  apply_immediately               = true
}

resource "aws_docdb_cluster_instance" "main" {
  identifier         = "${local.name}-1"
  cluster_identifier = aws_docdb_cluster.main.id
  instance_class     = "db.t3.medium"
}

locals {
  mq_host = split(":", trimprefix(aws_mq_broker.rabbitmq.instances[0].endpoints[0], "amqps://"))[0]

  # DocumentDB không hỗ trợ retryable writes; replicaSet=rs0 là tên cố định của DocumentDB.
  mongodb_uri = format(
    "mongodb://%s:%s@%s:%d/notification_db?replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false&authSource=admin",
    aws_docdb_cluster.main.master_username,
    var.docdb_password,
    aws_docdb_cluster.main.endpoint,
    aws_docdb_cluster.main.port,
  )
}
