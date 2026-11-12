# Hạ tầng production của OpenEvent trên AWS: ECS Fargate + RDS + Amazon MQ + MongoDB (ECS + EFS) + ALB + CloudFront.
# Sơ đồ và cách chạy: README.md cùng thư mục.

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.80"
    }
  }
}

provider "aws" {
  region = var.region

  # Gắn tag cho mọi tài nguyên để lọc trên Console (Resource Groups) và xem chi phí theo project.
  default_tags {
    tags = {
      Project   = "openevent"
      ManagedBy = "terraform"
    }
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name = "openevent"
  azs  = slice(data.aws_availability_zones.available.names, 0, 2)

  # Địa chỉ công khai của ứng dụng (CloudFront cấp HTTPS sẵn cho tên *.cloudfront.net).
  app_url = "https://${aws_cloudfront_distribution.app.domain_name}"
}
