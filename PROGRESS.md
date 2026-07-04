# Implementation Progress

Phased build plan for `async-job-processing-service`. Each phase is independently verifiable.

## Status overview

| Phase | Component | Status | Verified |
|-------|-----------|--------|----------|
| **1** | Maven scaffold + Spring Boot config | ✅ Complete | `mvn compile` ✓ |
| **2** | Flyway schema + JPA models + repositories | ✅ Complete | `mvn compile` ✓ |
| **3** | REST API (jobs, status, cancel, queue, ops) | ✅ Complete | unit tests ✓ |
| **4** | Transactional outbox + RabbitMQ dispatcher | ✅ Complete | unit tests ✓ |
| **5** | Worker pool + SampleJobHandler + retry/dead-letter | ✅ Complete | unit tests ✓ |
| **6** | Unit tests | ✅ Complete | **14 tests pass** |
| **7** | Integration tests (Testcontainers) | ✅ 10 tests written | runs in GitHub Actions CI |
| **8** | Docker Compose + smoke scripts | ✅ Code complete | needs Docker locally |
| **9** | CI/CD, Terraform, documentation | ✅ Complete | files present |

---

## Phase 1 — Scaffold

**Files:** `pom.xml`, `application.yml`, `AsyncJobsApplication.java`

```
Client → REST API → PostgreSQL + outbox (same TX) → Dispatcher → RabbitMQ → Workers
```

```bash
mvn -q compile
```

---

## Phase 2 — Database & domain

**Files:** `V1__init_schema.sql`, `model/*`, `repository/*`, `dto/*`

Tables: `jobs`, `job_attempts`, `dead_letter_jobs`, `outbox_events`, `system_state`

---

## Phase 3 — REST API

**Files:** `controller/*`, `service/JobService`, `JobStatusService`, `QueueDepthService`, `DrainService`

| Endpoint | Purpose |
|----------|---------|
| `POST /api/v1/jobs` | Submit job (202 QUEUED) |
| `GET /api/v1/jobs/{id}` | Status + result |
| `POST /api/v1/jobs/{id}/cancel` | Cancel queued/retry jobs |
| `GET /api/v1/queue/depth` | DB + optional RabbitMQ counts |
| `POST /api/v1/ops/drain` | Stop submissions/dispatch/work |
| `POST /api/v1/ops/resume` | Re-enable |

---

## Phase 4 — Outbox + dispatcher

**Files:** `OutboxService`, `OutboxDispatchService`, `OutboxDispatcher`, `RabbitMqEventPublisher`

- API never publishes directly to RabbitMQ
- Outbox polled with `FOR UPDATE SKIP LOCKED`
- `publish_after` enables delayed retry without worker sleep

---

## Phase 5 — Workers

**Files:** `JobWorker`, `JobExecutionService`, `SampleJobHandler`, `RetryService`

- Manual RabbitMQ ack after DB commit
- Atomic claim SQL with lease
- Handler types: `success`, `fail`, `failUntilAttempt`, `sleep`, `timeout`

---

## Phase 6 — Unit tests ✅

```bash
make test
# 14 tests, ~2 seconds
```

| Test class | Covers |
|------------|--------|
| `RetryServiceTest` | Exponential backoff + jitter |
| `DrainServiceTest` | Drain/resume flags |
| `JobServiceTest` | Idempotency key |
| `JobServiceCancelTest` | Cancel rules |
| `SampleJobHandlerTest` | Handler payload types |

---

## Phase 7 — Integration tests

```bash
make integration-test
# Requires Docker (Testcontainers pulls postgres:16 + rabbitmq:3-management)
```

**Note:** This environment has no Docker daemon. Tests are written and will run in CI or on your machine.

Covers: submit→complete, retries, dead letter, cancel, drain, idempotency, queue depth.

---

## Phase 8 — Local stack

```bash
cp .env.example .env
make local-up      # postgres + rabbitmq + api + worker + dispatcher
make smoke-test    # 10-step end-to-end script
make local-down
```

---

## Phase 9 — Production infra

- `.github/workflows/ci.yml` — test + build on PR/push
- `.github/workflows/docker-build.yml` — push to GHCR
- `.github/workflows/deploy.yml` — manual deploy
- `infra/terraform/` — DigitalOcean VPC, managed PG, droplets
- `scripts/do/` — provision, deploy, scale, destroy

---

## Next step for you

Open `async-job-processing-service/` in Cursor, then:

```bash
cd async-job-processing-service
make test                 # Phase 6 — should pass now
make integration-test     # Phase 7 — needs Docker
make local-up && make smoke-test   # Phase 8 — full stack
```
