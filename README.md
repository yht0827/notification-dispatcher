# Notification Dispatcher

알림 발송 요청을 비동기 파이프라인(Outbox + Redis Stream)으로 처리하는 멀티 모듈 프로젝트입니다.

## 문서 인덱스
- [01. 요구사항 정의서](docs/01-requirements.md)
- [02. 시퀀스 다이어그램](docs/02-sequence-diagrams.md)
- [03. 클래스 다이어그램](docs/03-class-diagrams.md)
- [04. ERD](docs/04-erd.md)

## 기술 스택

| 구분 | 스택 |
|---|---|
| Language / Build | Java 21, Gradle (Multi-module) |
| Framework | Spring Boot 3.5.12-SNAPSHOT, Spring Web, Spring Validation |
| Persistence | Spring Data JPA, MySQL, Flyway |
| Messaging / Concurrency | Redis Streams, Redisson (Distributed Lock), Spring Scheduling |
| API Docs | springdoc-openapi-starter-webmvc-ui 2.7.0 |
| Test | JUnit 5, Mockito, Spring Boot Test, Testcontainers (MySQL/Redis) |

## Swagger

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI 기본 정보 설정: `api/src/main/java/com/example/api/config/SwaggerConfig.java`

로컬 실행 후 확인

```bash
make up
make run
```

## 디렉토리 구조

```text
notification-dispatcher/
├── app/                  # Spring Boot 실행 모듈
├── api/                  # Controller, DTO, 예외 처리, Swagger
├── application/          # UseCase, Service, Port
├── domain/               # Entity, Enum, 도메인 규칙
├── infrastructure/       # JPA/Redis/Outbox/Lock/Sender 구현
├── docs/                 # 요구사항/시퀀스/클래스/ERD 문서
├── docker/               # 로컬 MySQL/Redis docker-compose
├── http/                 # API 호출 예시 (.http)
├── Makefile              # up/down/run/test/build 명령
└── settings.gradle       # 멀티 모듈 설정
```

## 아키텍처 구조

### 1) Layered + Hexagonal

┌──────────────────────────────────────────────┐
│ Frameworks / Drivers                         │
│ (Spring Boot, DB, RabbitMQ, Redis, 외부 API) │
│ ┌──────────────────────────────────────────┐ │
│ │ Adapters                                 │ │
│ │  - API (inbound adapter)                 │ │
│ │  - Infrastructure (outbound adapter)     │ │
│ │ ┌──────────────────────────────────────┐ │ │
│ │ │ Application                          │ │ │
│ │ │  - UseCase, Port(in/out), Service    │ │ │
│ │ │ ┌──────────────────────────────────┐ │ │ │
│ │ │ │ Domain                           │ │ │ │
│ │ │ │  - Entity, Value Object, Rule    │ │ │ │
│ │ │ └──────────────────────────────────┘ │ │ │
│ │ └──────────────────────────────────────┘ │ │
│ └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘

### 2) 비동기 발송 파이프라인

```text
POST /api/v1/notifications
  -> notification_group + notification + outbox 저장 (트랜잭션)
  -> OutboxPoller가 PENDING outbox를 WORK 스트림으로 발행
  -> RedisStreamConsumer가 WORK 메시지 소비
  -> DispatchLockManager로 중복 처리 방지
  -> ChannelSender(EMAIL/SMS/KAKAO)로 발송

실패 처리:
  - 재시도 가능: WAIT 스트림 이동 -> WaitScheduler가 재발행
  - 재시도 불가/한도 초과: DLQ 스트림 이동
```

### 3) 전체 흐름도 (Service + Outbox + Redis Streams)

```mermaid
flowchart LR
    subgraph S1["1) 요청 저장 (동기)"]
        Client["Client"] --> Api["Notification API"]
        Api --> Command["Command Service"]
        Command --> Db[("MySQL\nnotification_group\nnotification\noutbox")]
        Command --> Response["201 Created"]
    end

    subgraph S2["2) Outbox 발행 (비동기)"]
        Poller["OutboxPoller"] --> Db
        Db -->|"PENDING outbox"| Publisher["RedisStreamPublisher"]
        Publisher --> Work[("Redis Stream\nWORK")]
    end

    subgraph S3["3) WORK 소비 + 발송"]
        Work --> Consumer["RedisStreamConsumer"]
        Consumer --> Lock{"DispatchLockManager\n락 획득?"}
        Lock -->|"No"| Skip["중복 처리 방지 후 스킵"]
        Lock -->|"Yes"| Handler["RedisStreamRecordHandler"]
        Handler --> Sender["ChannelSender\n(EMAIL/SMS/KAKAO)"]
        Sender --> Result{"결과"}
    end

    Result -->|"성공"| Ack["ACK + 상태 저장"]
    Ack --> Db

    Result -->|"재시도 가능\n(한도 미만)"| Wait[("Redis Stream\nWAIT")]
    Wait --> WaitScheduler["RedisStreamWaitScheduler"]
    WaitScheduler -->|"nextRetryAt 도달"| Work

    Result -->|"재시도 불가\n또는 한도 초과"| Dlq[("Redis Stream\nDLQ")]
    Dlq --> Ops["모니터링 / 수동 대응"]
```
