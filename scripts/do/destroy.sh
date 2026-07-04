#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF_DIR="$ROOT_DIR/infra/terraform"

read -r -p "Destroy ALL DigitalOcean resources? Type 'destroy' to confirm: " confirm
if [[ "$confirm" != "destroy" ]]; then
  echo "Aborted"
  exit 1
fi

cd "$TF_DIR"
terraform destroy
