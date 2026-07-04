# Testing

## Unit tests

```bash
make test
```

Covers retry backoff, drain/resume, idempotency, handler behavior.

## Integration tests (Testcontainers)

```bash
make integration-test
```

Uses PostgreSQL + RabbitMQ containers to validate end-to-end flows:

- submit → dispatch → worker → status
- retries and dead letter
- cancel, drain, idempotency, queue depth

## Smoke tests (running stack)

```bash
make local-up
make smoke-test
```

## CI

GitHub Actions runs `mvn test`, `mvn verify`, and Docker build on each push/PR.
