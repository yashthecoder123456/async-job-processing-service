#!/usr/bin/env bash
# One-shot DigitalOcean provision + deploy + smoke test (Cursor/devcontainer friendly).
# Usage:
#   ./scripts/do/setup-cursor.sh
#   cp .env.do.example .env.do   # fill in credentials in Cursor editor
#   ./scripts/do/deploy-all.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF_DIR="$ROOT_DIR/infra/terraform"
TOOLS_DIR="$ROOT_DIR/.tools/bin"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.do}"

export PATH="$TOOLS_DIR:$PATH"

red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
step() { printf '\n==> %s\n' "$*"; }

if [[ ! -f "$ENV_FILE" ]]; then
  red "Missing $ENV_FILE"
  echo "In Cursor: copy .env.do.example to .env.do and add your DO token + password."
  echo "  cp .env.do.example .env.do"
  exit 1
fi

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

export DIGITALOCEAN_ACCESS_TOKEN="${DIGITALOCEAN_ACCESS_TOKEN:?DIGITALOCEAN_ACCESS_TOKEN required in .env.do}"

AUTO_APPROVE="${AUTO_APPROVE:-false}"
RUN_SMOKE_TEST="${RUN_SMOKE_TEST:-true}"
SKIP_LOCAL_BUILD="${SKIP_LOCAL_BUILD:-true}"
SSH_PUBLIC_KEY_PATH="${SSH_PUBLIC_KEY_PATH:-$HOME/.ssh/id_rsa.pub}"
SSH_PRIVATE_KEY_PATH="${SSH_PRIVATE_KEY_PATH:-$HOME/.ssh/id_rsa}"
SSH_ALLOWED_CIDR="${SSH_ALLOWED_CIDR:-0.0.0.0/0}"
TF_REGION="${TF_REGION:-nyc3}"
TF_WORKER_COUNT="${TF_WORKER_COUNT:-1}"
TF_DROPLET_SIZE="${TF_DROPLET_SIZE:-s-1vcpu-1gb}"
TF_POSTGRES_SIZE="${TF_POSTGRES_SIZE:-db-s-1vcpu-1gb}"
RABBITMQ_USERNAME="${RABBITMQ_USERNAME:-asyncjobs}"
RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:?RABBITMQ_PASSWORD required in .env.do}"
DOCKER_IMAGE="${DOCKER_IMAGE:-ghcr.io/yashthecoder123456/async-job-processing-service:latest}"

require_cmd() {
  if ! command -v "$1" >/dev/null; then
    red "Missing required command: $1"
    exit 1
  fi
}

step "Checking prerequisites (Cursor mode: no local Docker required)"
"$ROOT_DIR/scripts/do/setup-cursor.sh" >/dev/null
require_cmd doctl
require_cmd terraform
require_cmd python3
require_cmd curl
require_cmd ssh

if [[ ! -f "$SSH_PUBLIC_KEY_PATH" || ! -f "$SSH_PRIVATE_KEY_PATH" ]]; then
  red "SSH keys missing after setup"
  exit 1
fi

doctl auth init --access-token "$DIGITALOCEAN_ACCESS_TOKEN" >/dev/null 2>&1 || true
green "DigitalOcean token configured"

step "Writing infra/terraform/terraform.tfvars"
cat > "$TF_DIR/terraform.tfvars" <<EOF
do_token             = "$DIGITALOCEAN_ACCESS_TOKEN"
region               = "$TF_REGION"
project_name         = "async-job-processing-service"
ssh_public_key_path  = "$SSH_PUBLIC_KEY_PATH"
ssh_allowed_cidr     = "$SSH_ALLOWED_CIDR"
postgres_size        = "$TF_POSTGRES_SIZE"
postgres_node_count  = 1
droplet_size         = "$TF_DROPLET_SIZE"
worker_count         = $TF_WORKER_COUNT
docker_image         = "$DOCKER_IMAGE"
rabbitmq_username    = "$RABBITMQ_USERNAME"
rabbitmq_password    = "$RABBITMQ_PASSWORD"
app_env              = "prod"
EOF

step "Provisioning infrastructure (Terraform)"
cd "$TF_DIR"
terraform init -input=false
if [[ "$AUTO_APPROVE" == "true" ]]; then
  terraform apply -auto-approve -input=false
else
  terraform plan -out=tfplan
  read -r -p "Apply Terraform plan? [y/N] " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    red "Aborted before apply"
    exit 1
  fi
  terraform apply tfplan
fi
terraform output -json > outputs.json
green "Infrastructure provisioned"

step "Loading connection settings from Terraform outputs"
API_IP=$(python3 -c "import json; print(json.load(open('$TF_DIR/outputs.json'))['api_droplet_ip']['value'])")
RABBIT_IP=$(python3 -c "import json; print(json.load(open('$TF_DIR/outputs.json'))['rabbitmq_droplet_ip']['value'])")
PG_HOST=$(terraform output -raw postgres_host)
PG_PORT=$(terraform output -raw postgres_port)
PG_DB=$(terraform output -raw postgres_database)
PG_USER=$(terraform output -raw postgres_user)
PG_PASS=$(terraform output -raw postgres_password)

export PROD_DATABASE_URL="jdbc:postgresql://${PG_HOST}:${PG_PORT}/${PG_DB}?sslmode=require"
export PROD_DATABASE_USERNAME="$PG_USER"
export PROD_DATABASE_PASSWORD="$PG_PASS"
export PROD_RABBITMQ_HOST="$RABBIT_IP"
export PROD_RABBITMQ_PORT="5672"
export PROD_RABBITMQ_USERNAME="$RABBITMQ_USERNAME"
export PROD_RABBITMQ_PASSWORD="$RABBITMQ_PASSWORD"
export PROD_RABBITMQ_MANAGEMENT_URL="http://${RABBITMQ_USERNAME}:${RABBITMQ_PASSWORD}@${RABBIT_IP}:15672"
export DOCKER_IMAGE="$DOCKER_IMAGE"
export RUN_SMOKE_TEST="$RUN_SMOKE_TEST"
export SKIP_LOCAL_BUILD="$SKIP_LOCAL_BUILD"
export SSH_PRIVATE_KEY_PATH

step "Waiting for SSH on API droplet ($API_IP)"
for i in $(seq 1 60); do
  if ssh -i "$SSH_PRIVATE_KEY_PATH" -o StrictHostKeyChecking=no -o ConnectTimeout=5 root@"$API_IP" "echo ok" >/dev/null 2>&1; then
    green "SSH ready on API droplet"
    break
  fi
  if [[ "$i" -eq 60 ]]; then
    red "SSH to $API_IP timed out"
    exit 1
  fi
  sleep 10
done

step "Deploying application containers (pull image on droplets)"
cd "$ROOT_DIR"
chmod +x scripts/do/deploy.sh
./scripts/do/deploy.sh

step "Deployment complete"
green "API URL: http://${API_IP}:8080"
green "RabbitMQ management: http://${RABBIT_IP}:15672"
