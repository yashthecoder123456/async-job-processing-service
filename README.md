# Async Job Processing Service

Spring Boot async job platform: PostgreSQL transactional outbox → RabbitMQ → workers. API returns 202 immediately; poll `GET /jobs/{id}` for status.

**Image:** `ghcr.io/yashthecoder123456/async-job-processing-service:latest` (built on push to `main`)

## Quick start — local

```bash
cp .env.example .env
make local-up          # postgres, rabbitmq, api, worker, dispatcher
make smoke-test        # full E2E
make local-down
```

## Quick start — DigitalOcean droplet

Single-droplet deploy (postgres + rabbitmq + api + worker + dispatcher in Docker). Tested on Ubuntu 24.04, `s-2vcpu-4gb`.

**1. Create droplet** — Ubuntu 24.04, add your SSH key, allow inbound TCP **8080** (and 22 for SSH).

**2. SSH in and install Docker:**

```bash
ssh root@YOUR_DROPLET_IP

apt update && apt install -y docker.io git docker-compose
# Use docker-compose (hyphen), not "docker compose" — not available on default DO Ubuntu image
```

**3. Start the stack:**

```bash
git clone https://github.com/yashthecoder123456/async-job-processing-service.git
cd async-job-processing-service
docker pull ghcr.io/yashthecoder123456/async-job-processing-service:latest
docker-compose -f docker-compose.droplet.yml up -d
docker-compose -f docker-compose.droplet.yml ps   # all 5 services should be Up
```

**4. Wait ~30–60s, then health check (on droplet):**

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

**5. Test from your laptop** — replace `YOUR_DROPLET_IP`:

```bash
export API_URL=http://YOUR_DROPLET_IP:8080

curl -s "$API_URL/actuator/health"

JOB_ID=$(curl -sf -X POST "$API_URL/api/v1/jobs" \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"type":"success"},"priority":5,"maxRetries":3,"timeoutSeconds":10}' \
  | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)

echo "jobId=$JOB_ID"
curl -s "$API_URL/api/v1/jobs/$JOB_ID"    # repeat until status is SUCCEEDED
```

**6. Full automated test:**

```bash
# on droplet
API_URL=http://localhost:8080 ./scripts/demo-e2e.sh

# from laptop
API_URL=http://YOUR_DROPLET_IP:8080 ./scripts/demo-e2e.sh
```

**Useful commands on droplet:**

```bash
docker-compose -f docker-compose.droplet.yml logs --tail=50 api
docker-compose -f docker-compose.droplet.yml pull && docker-compose -f docker-compose.droplet.yml up -d   # update
docker-compose -f docker-compose.droplet.yml down                                                      # stop
```

## Test with curl

Set once: `export API_URL=http://YOUR_DROPLET_IP:8080` (or `http://localhost:8080` on the server).

| Action | Command |
|--------|---------|
| Health | `curl -s "$API_URL/actuator/health"` |
| Submit job | `curl -s -X POST "$API_URL/api/v1/jobs" -H 'Content-Type: application/json' -d '{"payload":{"type":"success"},"priority":5,"maxRetries":3,"timeoutSeconds":10}'` |
| Get status | `curl -s "$API_URL/api/v1/jobs/$JOB_ID"` |
| Queue depth | `curl -s "$API_URL/api/v1/queue/depth"` |
| Cancel | `curl -s -X POST "$API_URL/api/v1/jobs/$JOB_ID/cancel"` |
| Drain / resume | `curl -s -X POST "$API_URL/api/v1/ops/drain"` / `.../resume` |

**Built-in job types** (in `payload.type`): `success`, `fail`, `failUntilAttempt` (+ `succeedOnAttempt`), `timeout`, `sleep` (+ `sleepMs`).

More examples: [docs/api-examples.md](docs/api-examples.md)

## Architecture

```
Client → POST /api/v1/jobs → PostgreSQL (jobs + outbox, same TX)
       → OutboxDispatcher → RabbitMQ → Worker → PostgreSQL status update
Client → GET /api/v1/jobs/{id}
```

Details: [ARCHITECTURE.md](ARCHITECTURE.md) · Workers: [docs/workers.md](docs/workers.md)

## Multi-droplet production (optional)

Terraform + managed Postgres for separate API/worker/dispatcher droplets:

```bash
cp infra/terraform/terraform.tfvars.example infra/terraform/terraform.tfvars
./scripts/do/provision.sh && ./scripts/do/deploy.sh
```

See [DEPLOYMENT.md](DEPLOYMENT.md).

## CI/CD

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | push, PR | Tests + Docker build |
| `docker-build.yml` | push to main | Push image to GHCR |
| `deploy.yml` | manual | Deploy to DO + smoke test |

## Docs

[ARCHITECTURE.md](ARCHITECTURE.md) · [DEPLOYMENT.md](DEPLOYMENT.md) · [RUNBOOK.md](RUNBOOK.md) · [TESTING.md](TESTING.md) · [docs/api-examples.md](docs/api-examples.md)
