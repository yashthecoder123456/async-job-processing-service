# Worker pool and scaling

## How workers run

Each **worker container** is the same JAR with:

```bash
APP_ROLE=worker
WORKER_ENABLED=true
API_ENABLED=false
OUTBOX_DISPATCHER_ENABLED=false
```

On startup the worker:

1. Connects to PostgreSQL and RabbitMQ
2. Declares `job.execution.queue` (priority 1–10) and DLQ
3. Starts **N RabbitMQ consumers** (`WORKER_CONCURRENCY`, default 4)
4. Consumes messages with **manual ack**
5. Atomically claims jobs in PostgreSQL (`JobExecutionService.tryClaim`)
6. Executes `JobHandler` with per-job timeout
7. Updates `jobs` / `job_attempts` / `dead_letter_jobs`
8. Acks RabbitMQ only after DB commit

Key classes:

| Class | Role |
|-------|------|
| `worker/JobExecutionQueueConsumer.java` | RabbitMQ `@RabbitListener` entry point |
| `worker/JobExecutionOrchestrator.java` | Claim → handler → status update → ack |
| `worker/ExecutionMessage.java` | Queue message contract |
| `worker/JobHandler.java` | Pluggable handler interface |
| `worker/SampleJobHandler.java` | Demo handler (success/fail/retry/timeout) |
| `service/JobExecutionService.java` | Claim, attempts, retry, dead-letter |
| `config/ExecutionPipelineStartup.java` | Startup log for each pipeline stage |

## Local scale test

```bash
make local-up              # 2 worker replicas (docker compose)
make scale-local WORKERS=5 # scale to 5 worker containers
make smoke-test
```

## Production scale

| Knob | Effect |
|------|--------|
| `WORKER_CONCURRENCY` | Consumers **per** worker container |
| `worker_count` (Terraform) | Number of worker **droplets** |
| `./scripts/do/scale-workers.sh N` | Add worker droplets + redeploy |
| `DB_POOL_MAX_SIZE` | Postgres connections per instance |
| `OUTBOX_BATCH_SIZE` | Dispatcher throughput |

Horizontal scaling pattern:

```
                    ┌─ worker droplet 1 (WORKER_CONCURRENCY=8)
API → PG → outbox → RabbitMQ ─┼─ worker droplet 2
                    └─ worker droplet N
```

Multiple dispatchers are safe (`FOR UPDATE SKIP LOCKED` on outbox).

## Verify workers after deploy

```bash
# Submit job
curl -X POST http://<api-host>:8080/api/v1/jobs \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"type":"success"},"priority":5,"maxRetries":3,"timeoutSeconds":10}'

# Poll until SUCCEEDED
curl http://<api-host>:8080/api/v1/jobs/<jobId>

# Queue depth
curl http://<api-host>:8080/api/v1/queue/depth
```

Worker logs show `Worker pool ready` and `jobId`/`workerId` on each execution.
