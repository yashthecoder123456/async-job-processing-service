#!/usr/bin/env bash
set -euo pipefail
API_URL="${PROD_API_URL:?PROD_API_URL required}"
export API_URL
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
"$ROOT_DIR/scripts/local/smoke-test.sh"
