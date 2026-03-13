# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .

# 모듈별 build.gradle 복사 (의존성 캐시 레이어 분리)
COPY domain/build.gradle domain/
COPY application/build.gradle application/
COPY infrastructure/build.gradle infrastructure/
COPY api/build.gradle api/
COPY app/build.gradle app/
COPY mock/build.gradle mock/

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon --quiet || true

# 소스 복사 후 빌드
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
COPY api/src api/src
COPY app/src app/src
COPY mock/src mock/src

RUN ./gradlew :app:bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /workspace/app/build/libs/*.jar app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
