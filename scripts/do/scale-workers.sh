#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF_DIR="$ROOT_DIR/infra/terraform"
COUNT="${1:-2}"

cd "$TF_DIR"
terraform apply -auto-approve -var="worker_count=$COUNT"
terraform output -json > outputs.json
"$ROOT_DIR/scripts/do/deploy.sh"
