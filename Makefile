.PHONY: up down logs clean build test run \
        up-monitoring down-monitoring \
        up-all down-all \
        seed-read-compare replica-status

COMPOSE_LOCAL      = docker compose -f docker/docker-compose.local.yml
COMPOSE_MONITORING = docker compose -f docker/docker-compose.monitoring.yml

# ─────────────────────────────────────────
# 개발 기본 (MySQL + Redis + mysqld-exporter)
# ─────────────────────────────────────────
up:
	$(COMPOSE_LOCAL) up -d

down:
	$(COMPOSE_LOCAL) down

logs:
	$(COMPOSE_LOCAL) logs -f

clean:
	$(COMPOSE_LOCAL) down -v

# ─────────────────────────────────────────
# 모니터링 (Prometheus + Grafana)
# ─────────────────────────────────────────
up-monitoring:
	$(COMPOSE_MONITORING) up -d

down-monitoring:
	$(COMPOSE_MONITORING) down

# ─────────────────────────────────────────
# 전체 (개발 + 모니터링)
# ─────────────────────────────────────────
up-all: up up-monitoring

down-all:
	$(COMPOSE_LOCAL) down
	$(COMPOSE_MONITORING) down

# ─────────────────────────────────────────
# Gradle
# ─────────────────────────────────────────
build:
	./gradlew build -x test

test:
	./gradlew test

run:
	./gradlew :app:bootRun

mock:
	./gradlew :mock:bootRun

# ─────────────────────────────────────────
# 부하 테스트 (k6)
# ─────────────────────────────────────────
smoke:
	k6 run k6/smoke-test.js

load:
	k6 run k6/load-test.js

stress:
	k6 run k6/stress-test.js

seed-read-compare:
	./scripts/seed_read_compare_data.sh

replica-status:
	./scripts/check_replica_status.sh

compare-platform:
	k6 run --env LABEL=platform-thread k6/virtual-thread-compare.js

compare-virtual:
	k6 run --env LABEL=virtual-thread k6/virtual-thread-compare.js

# ─────────────────────────────────────────
# All-in-one
# ─────────────────────────────────────────
start: up
	@echo "Waiting for MySQL..."
	@sleep 5
	./gradlew :app:bootRun
