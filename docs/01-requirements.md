# 요구사항 정의서

> Notification Dispatcher 시스템 요구사항 정의 (현재 코드 기준)

## 목차

- [유비쿼터스 언어](#유비쿼터스-언어)
- [시스템 개요](#시스템-개요)
- [API 전체 요약](#api-전체-요약)
- [알림 발송](#알림-발송)
- [알림 조회](#알림-조회)
- [비동기 처리 규칙](#비동기-처리-규칙)
- [상태 전이 규칙](#상태-전이-규칙)
- [비기능적 요구사항](#비기능적-요구사항)
- [운영 체크포인트](#운영-체크포인트)

---

## 유비쿼터스 언어

> 모든 협업자가 동일한 언어로 도메인을 이해하고 소통하기 위한 용어 체계

| 한글 용어 | 영문 용어 | 설명 |
|-----------|-----------|------|
| 알림 | Notification | 수신자 1명에게 발송되는 개별 메시지 |
| 알림 그룹 | NotificationGroup | 동일 요청으로 생성된 알림 묶음 |
| 클라이언트 | Client | 알림 발송을 요청하는 서비스 (`order-service` 등) |
| 수신자 | Receiver | 알림 수신 대상 (이메일, 전화번호, 카카오 식별자) |
| 채널 | Channel | 알림 전달 방식 (`EMAIL`, `SMS`, `KAKAO`) |
| 멱등성 키 | Idempotency Key | 동일 요청 중복 처리를 막는 식별 키 |
| Outbox | Outbox | 트랜잭션 내 이벤트 저장 후 비동기 발행하는 패턴 |
| WORK 스트림 | WORK Stream | 실제 발송 처리 대상 메시지 스트림 |
| WAIT 스트림 | WAIT Stream | 재시도 대기 메시지 스트림 |
| DLQ | Dead Letter Queue | 재시도 불가 메시지 보관 스트림 |
| 분산 락 | Distributed Lock | 중복 발송 방지를 위한 Redis 락 |

---

## 시스템 개요

### 아키텍처

```text
Client
  -> API (NotificationController)
  -> Application (NotificationCommandService)
  -> DB 저장 (notification_group, notification, outbox)
  -> OutboxPoller
  -> Redis Stream WORK
  -> RedisStreamConsumer
  -> NotificationDispatchService
  -> ChannelSender(EMAIL/SMS/KAKAO)

실패 시: WORK -> WAIT(지수 백오프) -> WORK 재발행
최종 실패: WORK -> DLQ
```

### 핵심 패턴

| 패턴 | 설명 |
|------|------|
| Hexagonal Architecture | `api -> application(port) -> infrastructure(adapter)` 분리 |
| Transactional Outbox | 알림 저장과 이벤트 저장을 동일 트랜잭션으로 처리 |
| Redis Streams | Consumer Group 기반 비동기 처리 |
| Retry with WAIT Stream | 재시도 대기열과 스케줄러를 분리한 재처리 |
| Distributed Lock | notificationId 단위 중복 처리 방지 |
| Idempotency Key | `clientId + idempotencyKey` 기반 중복 요청 방지 |

### 모듈 구성

| 모듈 | 책임 |
|------|------|
| `domain` | 엔티티/도메인 규칙 (`Notification`, `NotificationGroup`, `Outbox`) |
| `application` | 유스케이스/서비스/포트 정의 |
| `infrastructure` | JPA, Redis Stream, Lock, Sender 구현 |
| `api` | HTTP Controller, DTO, 예외 응답 |
| `app` | Spring Boot 실행 진입점 (`@EnableScheduling`) |

---

## API 전체 요약

| 도메인 | 기능 | METHOD | URI | 인증 |
|--------|------|--------|-----|------|
| 알림 | 알림 발송 | POST | `/api/v1/notifications` | X |
| 알림 | 알림 묶음 목록 조회(커서) | GET | `/api/v1/notifications` | X |
| 알림 | 개별 알림 조회 | GET | `/api/v1/notifications/{notificationId}` | X |
| 알림 | 수신자별 알림 조회 | GET | `/api/v1/notifications?receiver={receiver}` | X |
| 그룹 | 그룹 상세 조회 | GET | `/api/v1/notifications/groups/{groupId}` | X |
| 그룹 | 클라이언트별 그룹 조회 | GET | `/api/v1/notifications/groups?clientId={clientId}` | X |

---

## 알림 발송

### 알림 발송 요청

| METHOD | URI | 설명 | 인증 |
|--------|-----|------|------|
| POST | `/api/v1/notifications` | 단일/대량 알림 발송 요청 | 불필요 |

#### 기능적 요구사항

- 수신자 목록(`receivers`) 크기에 따라 단건(`SINGLE`) 또는 대량(`BULK`) 그룹을 생성한다.
- `idempotencyKey`가 존재하고 동일 요청 이력이 있으면 기존 그룹을 재사용한다.
- 신규 요청일 경우 `NotificationGroup` 및 개별 `Notification`을 생성한다.
- 각 알림별로 `Outbox` 이벤트를 생성해 비동기 발송 파이프라인으로 전달한다.

#### 입력 제약

| 필드 | 제약 |
|------|------|
| `clientId` | 필수, 공백 불가 |
| `sender` | 필수, 공백 불가 |
| `title` | 필수, 공백 불가 |
| `content` | 필수, 공백 불가 |
| `channelType` | 필수, `EMAIL` / `SMS` / `KAKAO` |
| `receivers` | 필수, 최소 1개 이상 |
| `idempotencyKey` | 선택, 공백이면 `null` 처리 |

#### Happy Path

```text
1. 클라이언트가 POST /api/v1/notifications 호출
2. 서버가 idempotencyKey 정규화(trim/blank->null)
3. 기존 (clientId, idempotencyKey) 그룹 조회
4. 기존 그룹이 있으면 해당 그룹 반환
5. 없으면 NotificationGroup + Notification N건 생성
6. Notification별 Outbox 이벤트 N건 저장
7. 201 Created 응답 (groupId, totalCount)
8. OutboxPoller가 Outbox를 WORK 스트림으로 발행
9. Consumer가 WORK를 읽어 채널 발송 수행
```

#### Request Body

```json
{
  "clientId": "order-service",
  "sender": "MyShop",
  "title": "주문 완료",
  "content": "주문이 완료되었습니다. 주문번호: #12345",
  "channelType": "EMAIL",
  "receivers": ["user@example.com"],
  "idempotencyKey": "order-12345"
}
```

#### Response Body (201 Created)

```json
{
  "success": true,
  "data": {
    "groupId": 1,
    "totalCount": 1,
    "message": "알림 발송이 요청되었습니다."
  }
}
```

#### Fail Cases

| 케이스 | 설명 | HTTP 상태코드 |
|--------|------|---------------|
| 필수값 누락 | `clientId`, `sender`, `title` 등 누락/공백 | `400 BAD REQUEST` |
| 수신자 없음 | `receivers`가 빈 배열 | `400 BAD REQUEST` |
| 잘못된 채널 | 역직렬화 불가 채널 값 | `400 BAD REQUEST` |
| 서버 오류 | 예기치 않은 예외 | `500 INTERNAL SERVER ERROR` |

---

## 알림 조회

### 알림 묶음 목록 조회

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api/v1/notifications` | 알림 그룹 최신순 커서 조회 |

#### 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| `cursorId` | Long | X | - | 이전 페이지 마지막 그룹 ID |
| `size` | Integer | X | 20 | 조회 크기 (`1~100`) |

#### 응답 규칙

- 내부적으로 `size + 1`건 조회 후 `hasNext`를 계산한다.
- `nextCursorId`는 현재 응답 마지막 아이템의 `groupId`를 사용한다.

#### Response Body

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "groupId": 10,
        "title": "신규 프로모션",
        "content": "지금 가입 시 20% 할인",
        "createdAt": "2026-02-24T13:00:00",
        "totalCount": 3,
        "moreCount": 2
      }
    ],
    "hasNext": true,
    "nextCursorId": 10
  }
}
```

### 알림 그룹 상세 조회

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api/v1/notifications/groups/{groupId}` | 그룹 및 하위 알림 상세 조회 |

#### Response Body

```json
{
  "success": true,
  "data": {
    "groupId": 1,
    "clientId": "order-service",
    "sender": "MyShop",
    "title": "주문 완료",
    "content": "주문이 완료되었습니다.",
    "groupType": "SINGLE",
    "channelType": "EMAIL",
    "totalCount": 1,
    "sentCount": 1,
    "failedCount": 0,
    "pendingCount": 0,
    "completed": true,
    "createdAt": "2026-02-24T13:00:00",
    "notifications": [
      {
        "notificationId": 101,
        "receiver": "user@example.com",
        "status": "SENT",
        "sentAt": "2026-02-24T13:00:03",
        "failReason": null,
        "createdAt": "2026-02-24T13:00:00"
      }
    ]
  }
}
```

### 클라이언트별 그룹 조회

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api/v1/notifications/groups?clientId={clientId}` | 특정 클라이언트 그룹 목록 조회 |

### 개별 알림 조회

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api/v1/notifications/{notificationId}` | 알림 단건 조회 |

### 수신자별 알림 조회

| METHOD | URI | 설명 |
|--------|-----|------|
| GET | `/api/v1/notifications?receiver={receiver}` | 수신자 기준 목록 조회 |

#### 조회 API Fail Cases

| 케이스 | 설명 | HTTP 상태코드 |
|--------|------|---------------|
| `size` 범위 오류 | `size < 1` 또는 `size > 100` | `400 BAD REQUEST` |
| `cursorId` 범위 오류 | `cursorId <= 0` | `400 BAD REQUEST` |
| `receiver` 공백 | receiver 파라미터 빈 값 | `400 BAD REQUEST` |
| `clientId` 공백 | clientId 파라미터 빈 값 | `400 BAD REQUEST` |
| 그룹 미존재 | 존재하지 않는 `groupId` | `404 NOT FOUND` |
| 알림 미존재 | 존재하지 않는 `notificationId` | `404 NOT FOUND` |

---

## 비동기 처리 규칙

### Outbox Poller

- 스케줄: `@Scheduled(fixedDelay = outbox.poll-interval-millis, 기본 1000ms)`
- 조회: `OutboxStatus.PENDING` 기준 최대 100건
- 발행 성공 건만 `PROCESSED` 마킹 후 삭제
- 발행 실패 건은 삭제하지 않고 다음 폴링에서 재시도

### Redis Stream 소비

- WORK 스트림 Consumer Group 기반 수신
- 메시지 처리 전 `DispatchLockManager.tryAcquire(notificationId)` 수행
- 예외 분류:
  - `RetryableStreamMessageException` -> WAIT 스트림 전송
  - `NonRetryableStreamMessageException` -> DLQ 스트림 전송
- WAIT/DLQ 전송 성공 시 원본 WORK 메시지 ACK

### 재시도 전략

- 최대 재시도: `notification.stream.max-retry-count` (기본 3)
- 재시도 지연: `retryBaseDelayMillis * 2^retryCount`
- WAIT 스케줄러가 만료(`nextRetryAt <= now`) 메시지를 WORK로 재발행
- 재발행 시 `retryCount + 1`

### 시작 시 복구 전략

- 앱 시작 시 Consumer Group 없으면 생성
- WORK Pending 메시지를 조회해 WAIT로 이관 후 ACK
- 복구 중 일부 메시지 실패가 발생해도 나머지 복구는 계속 진행

---

## 상태 전이 규칙

### Notification 상태

```text
PENDING -> SENDING -> SENT
                   -> FAILED
PENDING -> CANCELED
```

| 상태 | 설명 | 종결 여부 |
|------|------|-----------|
| `PENDING` | 발송 대기 | X |
| `SENDING` | 발송 시도 중 | X |
| `SENT` | 발송 성공 | O |
| `FAILED` | 최종 실패 | O |
| `CANCELED` | 취소 | O |

> 구현 메모: 재시도는 Redis WAIT 스트림에서 관리하며, Notification 엔티티는 최종 결과(SENT/FAILED)만 기록한다.

### Outbox 상태

| 상태 | 설명 |
|------|------|
| `PENDING` | 발행 대기 |
| `PROCESSED` | 발행 완료 |
| `FAILED` | 발행 실패(상태 정의 존재, 현재 Poller 기본 경로는 delete-on-success) |

---

## 비기능적 요구사항

### 성능

- API 검증/조회는 동기 처리, 발송은 비동기 처리로 분리
- Outbox Poller와 WAIT Scheduler는 1초 주기로 동작
- 커서 조회는 `id DESC` 기반으로 페이징한다.

### 신뢰성

- 요청 저장과 이벤트 저장을 동일 트랜잭션으로 보장
- 분산 락으로 동일 알림 중복 처리 방지
- 재시도 가능한 오류는 WAIT 스트림에서 지수 백오프
- 재시도 불가 오류는 DLQ에 보관

### 확장성

- Consumer Group 구조로 컨슈머 확장 가능
- `ChannelSender` 전략 인터페이스로 채널 확장 가능

---

## 운영 체크포인트

- 로컬 실행: `make up`, `make run`, `make test`
- 프로덕션 필수 환경변수: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`
- 현재 채널 발송 구현(`EmailSender`, `SmsSender`, `KakaoSender`)은 외부 연동 전의 기본 스텁 로직이다.
- `notification_group`의 `(client_id, idempotency_key)`는 유니크 인덱스이며, `idempotency_key`가 `NULL`인 요청은 중복 허용된다.
- `Outbox` 엔티티는 존재하나, Flyway 마이그레이션 기준 스키마와 운영 스키마는 반드시 동기화해서 관리해야 한다.
