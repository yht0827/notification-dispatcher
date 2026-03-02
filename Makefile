.PHONY: up down logs clean build test run \
        up-monitoring down-monitoring \
        up-perf down-perf \
        up-all down-all

COMPOSE_LOCAL     = docker compose -f docker/docker-compose.local.yml
COMPOSE_MONITORING = docker compose -f docker/docker-compose.monitoring.yml
COMPOSE_NGRINDER  = docker compose -f docker/docker-compose.ngrinder.yml

# ─────────────────────────────────────────
# 개발 기본 (MySQL + Redis)
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
# 성능 테스트 (nGrinder)
# ─────────────────────────────────────────
up-perf:
	$(COMPOSE_NGRINDER) up -d

down-perf:
	$(COMPOSE_NGRINDER) down

# ─────────────────────────────────────────
# 전체 (개발 + 모니터링 + 성능 테스트)
# ─────────────────────────────────────────
up-all: up up-monitoring up-perf

down-all:
	$(COMPOSE_LOCAL) down
	$(COMPOSE_MONITORING) down
	$(COMPOSE_NGRINDER) down

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
# All-in-one
# ─────────────────────────────────────────
start: up
	@echo "Waiting for MySQL..."
	@sleep 5
	./gradlew :app:bootRun
