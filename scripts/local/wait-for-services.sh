#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
MAX_ATTEMPTS=60

echo "Waiting for API at $API_URL ..."
for i in $(seq 1 $MAX_ATTEMPTS); do
  if curl -sf "$API_URL/actuator/health" >/dev/null; then
    echo "API is healthy"
    exit 0
  fi
  sleep 2
done

echo "Timed out waiting for services"
exit 1
