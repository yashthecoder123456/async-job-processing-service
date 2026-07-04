# Runbook

## Check queue depth

```bash
curl http://localhost:8080/api/v1/queue/depth
```

Inspect DB counts and optional RabbitMQ ready/unacked metrics.

## RabbitMQ UI

Open `http://localhost:15672` (guest/guest locally). Inspect `job.execution.queue` and `job.execution.dlq`.

## Inspect dead letter jobs

```sql
SELECT * FROM dead_letter_jobs ORDER BY dead_lettered_at DESC LIMIT 20;
```

## Replay dead letter job manually

1. Inspect payload/error in `dead_letter_jobs`.
2. Fix root cause.
3. Submit a new job with same business payload (or custom admin script) — no automatic replay endpoint in v1.

## Enable drain

```bash
curl -X POST http://localhost:8080/api/v1/ops/drain
```

Stops submissions, dispatcher publishing, and workers starting new jobs. Running jobs may finish.

## Resume

```bash
curl -X POST http://localhost:8080/api/v1/ops/resume
```

## Scale workers

Production:

```bash
./scripts/do/scale-workers.sh 4
```

Local: increase `worker` replicas or `WORKER_CONCURRENCY`.

## Rotate secrets

1. Update managed DB/RabbitMQ credentials.
2. Update droplet env files / secrets in GitHub.
3. Redeploy containers with `./scripts/do/deploy.sh`.

## Debug stuck jobs

```sql
SELECT id, status, attempt_count, locked_by, locked_until, last_error, updated_at
FROM jobs
WHERE status IN ('QUEUED','RUNNING','RETRY_SCHEDULED')
ORDER BY updated_at;
```

Check outbox backlog:

```sql
SELECT event_type, status, count(*) FROM outbox_events GROUP BY 1,2;
```

## Outbox backlog

- Verify dispatcher pods running and `dispatcher_enabled=true` in `system_state`.
- Check dispatcher logs for publish failures.
- Inspect `outbox_events.last_error`.
