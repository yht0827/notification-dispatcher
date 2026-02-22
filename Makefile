.PHONY: up down logs clean build test run

# Docker
up:
	docker compose -f docker/docker-compose.local.yml up -d

down:
	docker compose -f docker/docker-compose.local.yml down

logs:
	docker compose -f docker/docker-compose.local.yml logs -f

clean:
	docker compose -f docker/docker-compose.local.yml down -v

# Gradle
build:
	./gradlew build -x test

test:
	./gradlew test

run:
	./gradlew :app:bootRun

# All-in-one
start: up
	@echo "Waiting for MySQL..."
	@sleep 5
	./gradlew :app:bootRun
