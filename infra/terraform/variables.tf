variable "region" {
  description = "Region AWS"
  type        = string
  default     = "ap-southeast-1"
}

variable "vpc_cidr" {
  description = "Dải IP của VPC"
  type        = string
  default     = "10.20.0.0/16"
}

variable "services_enabled" {
  description = "false: chỉ tạo mạng, dữ liệu, cluster (lần apply đầu, để tạo database trước). true: chạy các ECS Service."
  type        = bool
  default     = false
}

variable "image_prefix" {
  description = "Tiền tố image trên GHCR; tên đầy đủ là <prefix>-<service>:<tag>"
  type        = string
  default     = "ghcr.io/khuongjanmes/openevent"
}

variable "image_tag" {
  description = "Tag image dùng cho mọi service (commit SHA hoặc latest)"
  type        = string
  default     = "latest"
}

variable "db_multi_az" {
  description = "RDS chạy bản dự phòng ở AZ thứ hai (tạo lâu hơn, chi phí gấp đôi)"
  type        = bool
  default     = false
}

# ---- Cấu hình ứng dụng (cùng ý nghĩa với .env.example) ----

variable "jwt_secret" {
  description = "Khóa ký JWT, dùng chung cho identity-service và api-gateway (openssl rand -base64 48)"
  type        = string
  sensitive   = true
}

variable "bootstrap_admin_email" {
  type    = string
  default = ""
}

variable "bootstrap_admin_password" {
  type      = string
  default   = ""
  sensitive = true
}

variable "google_client_id" {
  type    = string
  default = ""
}

variable "cloudinary_cloud_name" {
  type    = string
  default = ""
}

variable "cloudinary_api_key" {
  type      = string
  default   = ""
  sensitive = true
}

variable "cloudinary_api_secret" {
  type      = string
  default   = ""
  sensitive = true
}

variable "payos_client_id" {
  type      = string
  default   = ""
  sensitive = true
}

variable "payos_api_key" {
  type      = string
  default   = ""
  sensitive = true
}

variable "payos_checksum_key" {
  type      = string
  default   = ""
  sensitive = true
}

variable "vnpay_tmn_code" {
  type      = string
  default   = ""
  sensitive = true
}

variable "vnpay_hash_secret" {
  type      = string
  default   = ""
  sensitive = true
}

variable "mail_host" {
  type    = string
  default = ""
}

variable "mail_port" {
  type    = string
  default = "587"
}

variable "mail_username" {
  type    = string
  default = ""
}

variable "mail_password" {
  type      = string
  default   = ""
  sensitive = true
}

variable "mail_from" {
  type    = string
  default = ""
}

# ---- Mật khẩu dữ liệu: sinh bằng  openssl rand -hex 16  (chỉ chữ thường và số, hợp lệ với cả RDS, Amazon MQ, MongoDB) ----

variable "db_password" {
  description = "Mật khẩu master của RDS PostgreSQL"
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[A-Za-z0-9]{16,64}$", var.db_password))
    error_message = "16-64 ký tự chữ và số."
  }
}

variable "mq_password" {
  description = "Mật khẩu user openevent của Amazon MQ"
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[A-Za-z0-9]{16,64}$", var.mq_password))
    error_message = "16-64 ký tự chữ và số (Amazon MQ không nhận , : =)."
  }
}

variable "mongo_password" {
  description = "Mật khẩu root của MongoDB (nằm trong MONGODB_URI)"
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[A-Za-z0-9]{16,64}$", var.mongo_password))
    error_message = "16-64 ký tự chữ và số."
  }
}
