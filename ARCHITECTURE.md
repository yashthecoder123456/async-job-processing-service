# Architecture

## Component responsibilities

| Component | Responsibility |
|-----------|----------------|
| REST API | Accept jobs, expose status/queue depth, drain/resume/cancel |
| PostgreSQL `jobs` | Source of truth for client-visible status and results |
| PostgreSQL `outbox_events` | Reliable bridge for publishing execution/lifecycle events |
| Outbox Dispatcher | Polls outbox, publishes `JOB_EXECUTION_REQUESTED` to RabbitMQ |
| RabbitMQ | Priority execution queue consumed by workers |
| Workers | Claim jobs atomically, execute handlers, update status |
| `dead_letter_jobs` | Audit trail for exhausted retries |

## Full lifecycle (HTTP → result)

```mermaid
sequenceDiagram
  participant C as Client
  participant A as REST API
  participant D as PostgreSQL
  participant O as Outbox Dispatcher
  participant R as RabbitMQ
  participant W as Worker
  participant H as JobHandler

  C->>A: POST /api/v1/jobs
  A->>D: TX insert jobs + outbox_events
  A-->>C: 202 QUEUED
  O->>D: poll outbox (publish_after <= now)
  O->>R: publish JOB_EXECUTION_REQUESTED
  W->>R: consume (manual ack)
  W->>D: read job, atomic claim
  W->>H: execute with timeout
  W->>D: TX update status/result
  W->>R: ack after DB commit
  C->>A: GET /api/v1/jobs/{id}
  A->>D: read jobs table only
  A-->>C: status + resultPayload
```

## Outbox dispatcher path

```mermaid
flowchart LR
  OE[(outbox_events)] -->|SKIP LOCKED poll| D[OutboxDispatcher]
  D -->|JOB_EXECUTION_REQUESTED| RMQ[(RabbitMQ job.execution.queue)]
  D -->|lifecycle events| LOG[Structured logs]
  OE -->|publish_after > now| WAIT[Delayed retry gate]
  WAIT --> OE
```

## Retry path

```mermaid
flowchart TD
  W[Worker attempt fails] --> R{Retries remain?}
  R -->|yes| RS[status=RETRY_SCHEDULED]
  RS --> OE[Insert outbox JOB_EXECUTION_REQUESTED]
  OE --> PA[publish_after = now + backoff+jitter]
  PA --> D[Dispatcher publishes when due]
  D --> RMQ[RabbitMQ]
  R -->|no| DL[dead_letter_jobs + DEAD_LETTERED]
```

## Dead letter path

```mermaid
flowchart TD
  F[Final failure or timeout exhausted] --> J[jobs.status=DEAD_LETTERED]
  J --> DL[(dead_letter_jobs)]
  J --> OE[outbox JOB_DEAD_LETTERED lifecycle event]
  C[Client GET /jobs/id] --> J
```

## Status API path

```mermaid
flowchart LR
  C[Client] -->|GET /jobs/id| API[JobStatusService]
  API --> J[(jobs table)]
  API --> C2[JobStatusResponse]
```

Status API never reads RabbitMQ or outbox.

## Drain and cancel

```mermaid
flowchart TD
  DR[POST /ops/drain] --> SS[submissions_enabled=false]
  DR --> SD[dispatcher_enabled=false]
  DR --> SW[workers_enabled=false]
  SS --> API[Reject new POST /jobs]
  SD --> O[Stop publishing execution events]
  SW --> W[Workers nack/requeue or skip new work]
  RUN[RUNNING jobs] --> FIN[May finish]

  CN[POST /jobs/id/cancel] --> CQ[QUEUED/RETRY_SCHEDULED -> CANCELLED]
  CQ --> OE2[outbox JOB_CANCELLED]
  W2[Worker sees CANCELLED] --> ACK[Skip and ack message]
```

## At-least-once semantics

| Stage | Guarantee | Duplicate scenario | Bound |
|-------|-----------|-------------------|-------|
| API → DB | Exactly-once insert per idempotencyKey | Duplicate POST with same key | Returns same jobId |
| Outbox → RabbitMQ | At-least-once publish | Dispatcher retry / crash | jobId + terminal status check |
| RabbitMQ → Worker | At-least-once delivery | Redelivery after nack/crash | Atomic claim + terminal skip |
| Handler execution | At-least-once | Redelivered message | Claim SQL + status checks |

We do **not** claim exactly-once execution. External side effects inside handlers must be idempotent.

## Why not a `job_queue` table?

Polling DB rows for execution couples throughput to DB polling and makes priority/delayed retry harder. RabbitMQ provides mature consumer scaling and priority queues.

## Why not read status from RabbitMQ?

Broker message state is ephemeral and not authoritative for business lifecycle. Clients need durable queryable status independent of broker retention.

## Duplicate execution bounds

- `jobId` identity
- Optional `idempotencyKey` on submission
- Atomic claim SQL with lease (`JobRepository.claimJob`, `JobExecutionService.tryClaim`)
- Skip if status is terminal/cancelled

## Priority

Queue declared with `x-max-priority=10`. Message priority set from request. This is practical broker priority, not global strict ordering.

## Delayed retry

Failed attempts create outbox events with future `publish_after` using exponential backoff + jitter. Workers do not sleep; dispatcher time-gates publishing.

## Failure scenarios

| Scenario | Behavior |
|----------|----------|
| API crash after DB commit | Outbox dispatcher eventually publishes |
| Dispatcher crash mid-batch | SKIP LOCKED + status transitions allow retry |
| Worker crash after claim | Lease expires; message may be redelivered; claim prevents double-run if completed |
| RabbitMQ unavailable | Outbox events remain pending/failed and retry |
| Duplicate delivery | Terminal status + claim SQL skip re-execution |

## Tradeoffs

- Additional dispatcher component vs simpler direct publish
- Operational complexity of RabbitMQ vs DB polling
- At-least-once semantics require idempotent handlers
