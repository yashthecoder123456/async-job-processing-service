#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"

poll_status() {
  local job_id="$1"
  local expected="$2"
  for _ in $(seq 1 60); do
    status=$(curl -sf "$API_URL/api/v1/jobs/$job_id" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
    if [[ "$status" == "$expected" ]]; then
      echo "Job $job_id reached $expected"
      return 0
    fi
    sleep 2
  done
  echo "Job $job_id did not reach $expected (last=$status)"
  return 1
}

echo "1. Submit success job"
SUCCESS_ID=$(curl -sf -X POST "$API_URL/api/v1/jobs" \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"type":"success"},"priority":5,"maxRetries":3,"timeoutSeconds":10}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['jobId'])")
poll_status "$SUCCESS_ID" SUCCEEDED

echo "2. Submit failUntilAttempt job"
RETRY_ID=$(curl -sf -X POST "$API_URL/api/v1/jobs" \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"type":"failUntilAttempt","succeedOnAttempt":3},"priority":5,"maxRetries":3,"timeoutSeconds":10}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['jobId'])")
poll_status "$RETRY_ID" SUCCEEDED

echo "3. Submit always-fail job"
FAIL_ID=$(curl -sf -X POST "$API_URL/api/v1/jobs" \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"type":"fail"},"priority":5,"maxRetries":1,"timeoutSeconds":10}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['jobId'])")
poll_status "$FAIL_ID" DEAD_LETTERED

echo "4. Queue depth"
curl -sf "$API_URL/api/v1/queue/depth" | python3 -m json.tool

echo "5. Drain"
curl -sf -X POST "$API_URL/api/v1/ops/drain" | python3 -m json.tool

echo "6. Resume"
curl -sf -X POST "$API_URL/api/v1/ops/resume" | python3 -m json.tool

echo "7. Cancel queued job"
CANCEL_ID=$(curl -sf -X POST "$API_URL/api/v1/jobs" \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"type":"sleep","sleepMs":10000},"priority":5,"maxRetries":0,"timeoutSeconds":30}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['jobId'])")
curl -sf -X POST "$API_URL/api/v1/jobs/$CANCEL_ID/cancel" | python3 -m json.tool
poll_status "$CANCEL_ID" CANCELLED

echo "Smoke tests passed"
