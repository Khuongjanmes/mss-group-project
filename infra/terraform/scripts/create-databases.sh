#!/usr/bin/env bash
# Tạo 5 database của các service trên RDS (identity_db, event_db, ticket_db, order_db, payment_db).
# RDS nằm trong private subnet nên máy cá nhân không kết nối thẳng được: script chạy một task ECS
# dùng image postgres (task definition openevent-db-init), chờ task xong rồi in log.
# Chạy trong infra/terraform sau lần "terraform apply" đầu tiên:  bash scripts/create-databases.sh
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash trên Windows: không đổi "/ecs/..." thành đường dẫn Windows

cluster=$(terraform output -raw cluster_name)
task_def=$(terraform output -raw db_init_task_definition)
subnets=$(terraform output -raw private_subnet_ids)
sg=$(terraform output -raw task_security_group_id)

echo "Chạy task tạo database trên cluster $cluster..."
task_arn=$(aws ecs run-task \
  --cluster "$cluster" \
  --task-definition "$task_def" \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$subnets],securityGroups=[$sg],assignPublicIp=DISABLED}" \
  --query 'tasks[0].taskArn' --output text)

echo "Task: $task_arn"
aws ecs wait tasks-stopped --cluster "$cluster" --tasks "$task_arn"

exit_code=$(aws ecs describe-tasks --cluster "$cluster" --tasks "$task_arn" \
  --query 'tasks[0].containers[0].exitCode' --output text)

echo "--- Log ---"
aws logs tail /ecs/openevent/db-init --since 15m --format short || true

if [ "$exit_code" != "0" ]; then
  reason=$(aws ecs describe-tasks --cluster "$cluster" --tasks "$task_arn" \
    --query 'tasks[0].stoppedReason' --output text)
  echo "Lỗi: exit code $exit_code ($reason)" >&2
  exit 1
fi
echo "Xong."
