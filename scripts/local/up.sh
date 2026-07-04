#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
docker compose up -d --build --scale worker="${WORKERS:-2}"
"$ROOT_DIR/scripts/local/wait-for-services.sh"
