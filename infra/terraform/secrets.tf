# Một secret JSON trong Secrets Manager chứa mọi giá trị nhạy cảm. Task definition chỉ tham chiếu
# "<arn>:<KEY>::", ECS tự đọc lúc khởi động task nên giá trị không hiện trên Console hay trong task definition.

locals {
  secret_values = {
    JWT_SECRET               = var.jwt_secret
    DB_PASSWORD              = var.db_password
    RABBITMQ_PASSWORD        = var.mq_password
    MONGODB_URI              = local.mongodb_uri
    MONGO_PASSWORD           = var.mongo_password
    BOOTSTRAP_ADMIN_PASSWORD = var.bootstrap_admin_password
    CLOUDINARY_API_KEY       = var.cloudinary_api_key
    CLOUDINARY_API_SECRET    = var.cloudinary_api_secret
    PAYOS_CLIENT_ID          = var.payos_client_id
    PAYOS_API_KEY            = var.payos_api_key
    PAYOS_CHECKSUM_KEY       = var.payos_checksum_key
    VNPAY_TMN_CODE           = var.vnpay_tmn_code
    VNPAY_HASH_SECRET        = var.vnpay_hash_secret
    MAIL_PASSWORD            = var.mail_password
  }

  # Giá trị để trống (tính năng chưa cấu hình) thì không đưa vào, service dùng mặc định như khi chạy compose.
  secret_keys_set = nonsensitive(toset([for k, v in local.secret_values : k if v != ""]))
}

resource "aws_secretsmanager_secret" "app" {
  name                    = "${local.name}/app"
  description             = "Secret cua OpenEvent (JWT, mat khau DB/MQ, khoa thanh toan, mail)"
  recovery_window_in_days = 0 # destroy xóa ngay, apply lại không bị trùng tên
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id     = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({ for k, v in local.secret_values : k => v if v != "" })
}
