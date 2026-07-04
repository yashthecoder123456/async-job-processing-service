# Deployment Guide

## Local (Docker Compose)

```bash
make local-up
make smoke-test
make local-down
```

Services: postgres, rabbitmq, api, worker, dispatcher.

## DigitalOcean

### Prerequisites

- `doctl`, `terraform`, `docker`
- `DIGITALOCEAN_ACCESS_TOKEN`
- SSH key pair

### Provision

```bash
cp infra/terraform/terraform.tfvars.example infra/terraform/terraform.tfvars
# edit terraform.tfvars
./scripts/do/provision.sh
```

Creates VPC, managed PostgreSQL, RabbitMQ droplet, API/dispatcher/worker droplets, firewall.

### Deploy

Set production secrets:

```bash
export PROD_DATABASE_URL=...
export PROD_DATABASE_USERNAME=...
export PROD_DATABASE_PASSWORD=...
export PROD_RABBITMQ_HOST=...
export PROD_RABBITMQ_USERNAME=...
export PROD_RABBITMQ_PASSWORD=...
./scripts/do/deploy.sh
```

### Smoke test production

```bash
export PROD_API_URL=http://<api-ip>:8080
./scripts/do/smoke-test-prod.sh
```

### Destroy

```bash
./scripts/do/destroy.sh
```

## GitHub Actions

- `ci.yml` — tests + Docker build on PR/push
- `docker-build.yml` — push image to GHCR on main
- `deploy.yml` — manual deploy + smoke test

Required secrets listed in README.

## Container role env vars

| Role | APP_ROLE | API | WORKER | DISPATCHER |
|------|----------|-----|--------|------------|
| API | api | true | false | false |
| Worker | worker | false | true | false |
| Dispatcher | dispatcher | false | false | true |
