#!/usr/bin/env bash
# Tạm tắt / bật lại các ECS service để tiết kiệm chi phí khi không dùng (ví dụ ban đêm).
#   bash scripts/scale.sh down     # mọi service về 0 task (Fargate không tính tiền)
#   bash scripts/scale.sh up       # bật lại đúng số task như Terraform khai báo, chờ tới khi ổn định
#   bash scripts/scale.sh status
# Dữ liệu vẫn còn: RDS, Amazon MQ, EFS (MongoDB) không bị đụng tới. RDS, MQ, NAT, ALB vẫn tính tiền theo giờ;
# muốn hết hẳn chi phí thì dùng terraform destroy.
# Lưu ý: đang "down" mà chạy terraform apply thì autoscaling của ticket-service về lại tối thiểu 2 task.
set -euo pipefail
export MSYS_NO_PATHCONV=1 # Git Bash trên Windows

CLUSTER=openevent
# <service>:<số task khi chạy>. Thứ tự bật: MongoDB và Eureka trước, các service đăng ký vào Eureka sau.
FIRST="mongodb:1 eureka-server:1"
REST="identity-service:1 event-service:1 ticket-service:2 order-service:1 payment-service:1 notification-service:1 api-gateway:2 web:1"
TICKET="service/$CLUSTER/ticket-service"

names() { for s in "$@"; do printf '%s ' "${s%%:*}"; done; }

scale() { # scale <zero|keep> <service:count>...
  local mode=$1; shift
  for s in "$@"; do
    local n=${s##*:}; [ "$mode" = "zero" ] && n=0
    aws ecs update-service --cluster "$CLUSTER" --service "${s%%:*}" --desired-count "$n" \
      --query 'service.[serviceName,desiredCount]' --output text
  done
}

ticket_autoscaling() { # ticket_autoscaling <min> <suspended true|false>
  aws application-autoscaling register-scalable-target \
    --service-namespace ecs --scalable-dimension ecs:service:DesiredCount --resource-id "$TICKET" \
    --min-capacity "$1" --max-capacity 4 \
    --suspended-state "DynamicScalingInSuspended=$2,DynamicScalingOutSuspended=$2,ScheduledScalingSuspended=$2" > /dev/null
}

status() {
  aws ecs describe-services --cluster "$CLUSTER" --services $(names $FIRST $REST) \
    --query 'services[].[serviceName,runningCount,desiredCount]' --output text |
    awk '{printf "  %-22s running %s / desired %s\n", $1, $2, $3}'
}

case "${1:-}" in
  down)
    # Tắt autoscaling trước, không thì nó bật lại ticket-service lên tối thiểu 2 task.
    ticket_autoscaling 0 true
    scale zero $REST $FIRST
    echo "Đã đưa mọi service về 0 task."
    ;;
  up)
    scale keep $FIRST
    echo "Chờ MongoDB và Eureka ổn định..."
    aws ecs wait services-stable --cluster "$CLUSTER" --services $(names $FIRST)
    scale keep $REST
    ticket_autoscaling 2 false
    echo "Chờ các service ổn định (thường 3-4 phút)..."
    aws ecs wait services-stable --cluster "$CLUSTER" --services $(names $REST)
    status
    ;;
  status)
    status
    ;;
  *)
    echo "Dùng: bash scripts/scale.sh down|up|status" >&2
    exit 1
    ;;
esac
