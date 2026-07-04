# API Examples

Base URL: `http://localhost:8080`

## Submit success job

```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"type":"success"},"priority":5,"maxRetries":3,"timeoutSeconds":10}'
```

## Submit failUntilAttempt job

```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"type":"failUntilAttempt","succeedOnAttempt":3},"priority":5,"maxRetries":3,"timeoutSeconds":10}'
```

## Submit timeout job

```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H 'Content-Type: application/json' \
  -d '{"payload":{"type":"timeout"},"priority":5,"maxRetries":1,"timeoutSeconds":2}'
```

## Get status

```bash
curl http://localhost:8080/api/v1/jobs/{jobId}
```

## Queue depth

```bash
curl http://localhost:8080/api/v1/queue/depth
```

## Cancel

```bash
curl -X POST http://localhost:8080/api/v1/jobs/{jobId}/cancel
```

## Drain / Resume

```bash
curl -X POST http://localhost:8080/api/v1/ops/drain
curl -X POST http://localhost:8080/api/v1/ops/resume
```
