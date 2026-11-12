# Hạ tầng AWS (Terraform)

Production của OpenEvent chạy trên **AWS ECS Fargate**: mỗi service là một ECS Service riêng; PostgreSQL và RabbitMQ dùng dịch vụ managed của AWS (RDS, Amazon MQ), MongoDB tự chạy trên ECS với dữ liệu trên EFS. Toàn bộ hạ tầng khai báo trong thư mục này (Infrastructure as Code): `terraform apply` để tạo, `terraform destroy` để xóa sạch.

## Kiến trúc

```
Người dùng ──HTTPS──► CloudFront (https://<id>.cloudfront.net)
                          │ HTTP, ALB chỉ nhận IP của CloudFront
                          ▼
                    ALB (public subnet, 2 AZ)
                     ├── /api/*  ─► api-gateway  (2 task)
                     └── /*      ─► web: SPA + Caddy (1 task)

Private subnet (ap-southeast-1a, 1b) ─ ECS cluster "openevent" (Fargate)
   eureka-server (1) ◄── eureka.openevent.local (Cloud Map)
   identity (1) · event (1) · ticket (2, tự scale 2–4 theo CPU) · order (1) · payment (1) · notification (1)
        │                    │                                   │
   RDS PostgreSQL 16    Amazon MQ (RabbitMQ 3.13, AMQPS 5671)   mongodb (ECS, 1 task) ◄── mongodb.openevent.local
   5 database           Saga, sự kiện giữa các service          notification_db, dữ liệu trên EFS

NAT Gateway: task gọi ra Internet (kéo image GHCR, payOS, VNPay, Gmail, Cloudinary)
Secrets Manager "openevent/app": JWT, mật khẩu DB/MQ, khóa thanh toán, mail
CloudWatch Logs: /ecs/openevent/<service>, giữ 7 ngày; Container Insights cho CPU/RAM
```

Không sửa code service so với khi chạy `docker compose`. Khác biệt chỉ nằm ở biến môi trường:

| Biến | Compose | ECS |
| --- | --- | --- |
| `EUREKA_URL` | `http://eureka-server:8761/eureka` | `http://eureka.openevent.local:8761/eureka` |
| `DB_HOST` | `postgres` | endpoint RDS |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | `rabbitmq`, 5672 | endpoint Amazon MQ, 5671 + `SPRING_RABBITMQ_SSL_ENABLED=true` |
| `MONGODB_URI` | `mongodb://mongodb:27017/...` | `mongodb://openevent:***@mongodb.openevent.local:27017/...` (lấy từ Secrets Manager) |
| `SPRING_CLOUD_INETUTILS_PREFERREDNETWORKS` | không có | `10.20`: Eureka đăng ký IP của task trong VPC |

| File | Nội dung |
| --- | --- |
| `network.tf` | VPC, subnet public/private trên 2 AZ, NAT, security group |
| `data.tf` | RDS PostgreSQL, Amazon MQ |
| `mongodb.tf` | MongoDB trên ECS, EFS lưu dữ liệu, tên `mongodb.openevent.local` |
| `secrets.tf` | Secrets Manager |
| `ecs.tf` | Cluster, IAM role, Cloud Map, task definition + service của 9 thành phần, autoscale ticket-service, task tạo database |
| `edge.tf` | ALB, target group, CloudFront |

## Chạy lần đầu

Cần: [Terraform](https://developer.hashicorp.com/terraform/install) ≥ 1.6 (`winget install Hashicorp.Terraform`), AWS CLI đã `aws configure` với tài khoản có quyền admin.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # điền secret; services_enabled = false
terraform init

# 1. Mạng, dữ liệu, cluster, CloudFront (~20–30 phút, Amazon MQ tạo lâu nhất)
terraform apply

# 2. Tạo 5 database trên RDS
bash scripts/create-databases.sh

# 3. Bật các service: sửa services_enabled = true trong terraform.tfvars
terraform apply

terraform output app_url                        # địa chỉ ứng dụng
```

Sau khi có `app_url`:
- Google Cloud Console → OAuth client → *Authorized JavaScript origins*: thêm `app_url`.
- my.payos.vn → webhook: `<app_url>/api/payments/payos-webhook`.
- Nạp dữ liệu demo (ở thư mục gốc repo): `GATEWAY_URL=<app_url> node scripts/seed.mjs`.

## Vận hành

```bash
# Deploy lại một service với image mới nhất trên GHCR (task definition dùng tag image_tag, mặc định latest)
aws ecs update-service --cluster openevent --service ticket-service --force-new-deployment

# Xem log
aws logs tail /ecs/openevent/ticket-service --follow

# Vào container (ECS Exec, cần Session Manager plugin)
aws ecs execute-command --cluster openevent --task <task-id> --container ticket-service --interactive --command sh

# Tạm tắt khi không dùng (ban đêm): mọi service về 0 task, dữ liệu giữ nguyên; bật lại mất 3-4 phút
bash scripts/scale.sh down
bash scripts/scale.sh up

# Xóa toàn bộ hạ tầng (dữ liệu RDS và MongoDB mất, không giữ snapshot)
terraform destroy
```

Trên AWS Console (region Singapore): **ECS → Clusters → openevent** xem từng service, task, log; **EC2 → Load Balancers / Target groups** xem health check; **CloudFront**, **RDS**, **Amazon MQ**, **EFS**, **Secrets Manager**. Mọi tài nguyên có tag `Project=openevent`.

## Giới hạn đã biết

- Tài khoản AWS đang ở **gói free** (credit có hạn): gói này không cho dùng DocumentDB nên MongoDB tự chạy trên ECS + EFS. Một task duy nhất, không có replica set; production thật dùng DocumentDB hoặc MongoDB Atlas. Chạy xong nhớ `terraform destroy` để không hết credit.

- Một NAT Gateway và Amazon MQ một broker, đặt ở AZ `1a`: AZ đó hỏng thì task ở `1b` vẫn chạy nhưng không gọi ra Internet và không gửi/nhận message được. Production thật dùng NAT mỗi AZ và broker `CLUSTER_MULTI_AZ`.
- RDS mặc định một AZ (`db_multi_az = false`) để tạo nhanh; bật `true` để có bản dự phòng.
- Eureka chạy một task: task chết thì ECS dựng lại sau khoảng 1–2 phút, trong lúc đó các service dùng danh sách đã cache.
- State Terraform để ở máy chạy lệnh (`terraform.tfstate`, chứa mật khẩu, đã nằm trong `.gitignore`). Làm nhóm nên chuyển sang backend S3.
- Chưa có CD: deploy bằng `update-service` như trên. Workflow Deploy hiện vẫn đẩy lên EC2.
