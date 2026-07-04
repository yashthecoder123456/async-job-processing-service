#!/usr/bin/env bash
# End-to-end demo: health → submit → worker → SUCCEEDED (+ retry + dead letter + queue depth)
# Usage:
#   On droplet:  API_URL=http://localhost:8080 ./scripts/demo-e2e.sh
#   From Mac:    API_URL=http://YOUR_DROPLET_IP:8080 ./scripts/demo-e2e.sh
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"

green() { printf '\033[0;32m✓ %s\033[0m\n' "$*"; }
red()   { printf '\033[0;31m✗ %s\033[0m\n' "$*"; }
step()  { printf '\n==> %s\n' "$*"; }

json_field() {
  local json="$1"
  local field="$2"
  echo "$json" | grep -o "\"${field}\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
}

  local job_id="$1"
  local expected="$2"
  local max="${3:-60}"
  local status=""
  for _ in $(seq 1 "$max"); do
    body=$(curl -sf "$API_URL/api/v1/jobs/$job_id")
    status=$(json_field "$body" "status")
    echo "  poll: $status"
    if [[ "$status" == "$expected" ]]; then
      green "Job $job_id → $expected"
      echo "$body"
      return 0
    fi
    sleep 2
  done
  red "Job $job_id stuck at $status (expected $expected)"
  return 1
}

submit_job() {
  local payload="$1"
  curl -sf -X POST "$API_URL/api/v1/jobs" \
    -H 'Content-Type: application/json' \
    -d "$payload"
}

step "1/5 Health check"
health=$(curl -sf "$API_URL/actuator/health")
echo "$health"
echo "$health" | grep -q '"status":"UP"' || { red "API not UP"; exit 1; }
green "API is UP"

step "2/5 Submit success job → expect SUCCEEDED"
resp=$(submit_job '{"payload":{"type":"success"},"priority":5,"maxRetries":3,"timeoutSeconds":10}')
job_id=$(json_field "$resp" "jobId")
echo "  jobId=$job_id status=$(json_field "$resp" "status")"
poll_status "$job_id" SUCCEEDED >/dev/null

step "3/5 Submit failUntilAttempt → expect SUCCEEDED after retries"
resp=$(submit_job '{"payload":{"type":"failUntilAttempt","succeedOnAttempt":3},"priority":5,"maxRetries":3,"timeoutSeconds":10}')
retry_id=$(json_field "$resp" "jobId")
echo "  jobId=$retry_id"
poll_status "$retry_id" SUCCEEDED 90 >/dev/null

step "4/5 Submit always-fail job → expect DEAD_LETTERED"
resp=$(submit_job '{"payload":{"type":"fail"},"priority":5,"maxRetries":1,"timeoutSeconds":10}')
fail_id=$(json_field "$resp" "jobId")
echo "  jobId=$fail_id"
poll_status "$fail_id" DEAD_LETTERED 90 >/dev/null

step "5/5 Queue depth"
curl -sf "$API_URL/api/v1/queue/depth"
echo ""

green "ALL END-TO-END TESTS PASSED"
echo "API: $API_URL"
