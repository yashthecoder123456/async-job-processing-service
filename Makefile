.PHONY: build test integration-test local-up local-down migrate smoke-test logs clean

build:
	mvn -q -DskipTests package

test:
	mvn -q test

integration-test:
	mvn -q verify

local-up:
	./scripts/local/up.sh

local-down:
	./scripts/local/down.sh

migrate:
	mvn -q flyway:migrate -Dflyway.url=$${DATABASE_URL} -Dflyway.user=$${DATABASE_USERNAME} -Dflyway.password=$${DATABASE_PASSWORD}

smoke-test:
	./scripts/local/smoke-test.sh

logs:
	docker compose logs -f

clean:
	mvn -q clean
	docker compose down -v --remove-orphans 2>/dev/null || true
