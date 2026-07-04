#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF_DIR="$ROOT_DIR/infra/terraform"
IMAGE="${DOCKER_IMAGE:-ghcr.io/yashthecoder123456/async-job-processing-service:latest}"
WORKER_CONCURRENCY="${WORKER_CONCURRENCY:-8}"

cd "$ROOT_DIR"
mvn -q -DskipTests package
docker build -t "$IMAGE" .

if [[ -n "${GHCR_TOKEN:-}" ]]; then
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:-github}" --password-stdin
  docker push "$IMAGE"
fi

API_IP=$(python3 -c "import json; print(json.load(open('$TF_DIR/outputs.json'))['api_droplet_ip']['value'])")
WORKER_IPS=$(python3 -c "import json; print(' '.join(json.load(open('$TF_DIR/outputs.json'))['worker_droplet_ips']['value']))")
DISPATCHER_IP=$(python3 -c "import json; print(json.load(open('$TF_DIR/outputs.json'))['dispatcher_droplet_ip']['value'])")

deploy_node() {
  local ip="$1"
  local role="$2"
  local api_enabled="$3"
  local worker_enabled="$4"
  local dispatcher_enabled="$5"
  local port_map="$6"
  ssh -o StrictHostKeyChecking=no root@"$ip" "docker pull $IMAGE && docker rm -f asyncjobs-$role 2>/dev/null || true && docker run -d --name asyncjobs-$role --restart unless-stopped \
    -e DATABASE_URL='${PROD_DATABASE_URL}' \
    -e DATABASE_USERNAME='${PROD_DATABASE_USERNAME}' \
    -e DATABASE_PASSWORD='${PROD_DATABASE_PASSWORD}' \
    -e RABBITMQ_HOST='${PROD_RABBITMQ_HOST}' \
    -e RABBITMQ_PORT='${PROD_RABBITMQ_PORT:-5672}' \
    -e RABBITMQ_USERNAME='${PROD_RABBITMQ_USERNAME}' \
    -e RABBITMQ_PASSWORD='${PROD_RABBITMQ_PASSWORD}' \
    -e RABBITMQ_VHOST='${PROD_RABBITMQ_VHOST:-/}' \
    -e RABBITMQ_MANAGEMENT_URL='${PROD_RABBITMQ_MANAGEMENT_URL:-}' \
    -e APP_ROLE='$role' \
    -e API_ENABLED='$api_enabled' \
    -e WORKER_ENABLED='$worker_enabled' \
    -e OUTBOX_DISPATCHER_ENABLED='$dispatcher_enabled' \
    -e WORKER_CONCURRENCY='${WORKER_CONCURRENCY}' \
    -e OUTBOX_BATCH_SIZE='${OUTBOX_BATCH_SIZE:-100}' \
    -e OUTBOX_POLL_INTERVAL_MS='${OUTBOX_POLL_INTERVAL_MS:-250}' \
    -e DB_POOL_MAX_SIZE='${DB_POOL_MAX_SIZE:-30}' \
    -e SPRING_PROFILES_ACTIVE='prod' \
    $port_map \
    $IMAGE"
}

deploy_node "$API_IP" api true false false "-p 8080:8080"
deploy_node "$DISPATCHER_IP" dispatcher false false true ""
for ip in $WORKER_IPS; do
  deploy_node "$ip" worker false true false ""
done

echo "Waiting for API health (Flyway migrations run on startup)..."
for i in $(seq 1 60); do
  if curl -sf "http://${API_IP}:8080/actuator/health" | grep -q UP; then
    echo "API healthy after ${i} attempt(s)"
    break
  fi
  if [[ "$i" -eq 60 ]]; then
    echo "API did not become healthy in time"
    exit 1
  fi
  sleep 5
done

if [[ -n "${RUN_SMOKE_TEST:-true}" ]]; then
  export PROD_API_URL="http://${API_IP}:8080"
  "$ROOT_DIR/scripts/do/smoke-test-prod.sh"
fi

echo "Deployment complete"
