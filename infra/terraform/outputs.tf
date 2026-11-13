output "app_url" {
  description = "Địa chỉ ứng dụng (mở trên trình duyệt)"
  value       = local.app_url
}

output "alb_dns_name" {
  description = "DNS của ALB (chỉ CloudFront gọi được)"
  value       = aws_lb.main.dns_name
}

output "cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "rds_endpoint" {
  value = aws_db_instance.postgres.address
}

output "rabbitmq_host" {
  value = local.mq_host
}

# Dùng cho scripts/create-databases.sh (aws ecs run-task).
output "db_init_task_definition" {
  value = aws_ecs_task_definition.db_init.arn
}

output "private_subnet_ids" {
  value = join(",", aws_subnet.private[*].id)
}

output "task_security_group_id" {
  value = aws_security_group.tasks.id
}
