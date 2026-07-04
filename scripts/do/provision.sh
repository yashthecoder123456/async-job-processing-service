#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF_DIR="$ROOT_DIR/infra/terraform"

"$ROOT_DIR/scripts/do/check-prereqs.sh"

cd "$TF_DIR"
terraform init
terraform plan -out=tfplan

read -r -p "Apply Terraform plan? [y/N] " confirm
if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
  echo "Aborted"
  exit 1
fi

terraform apply tfplan
terraform output -json > outputs.json
echo "Outputs written to $TF_DIR/outputs.json"
