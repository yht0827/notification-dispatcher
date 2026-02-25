# 시퀀스 다이어그램

> Notification Dispatcher 주요 런타임 흐름

## 목차

- [알림 발송 요청 (동기)](#알림-발송-요청-동기)
- [Outbox 발행 (DB -> Redis)](#outbox-발행-db---redis)
- [WORK 소비 및 발송 성공](#work-소비-및-발송-성공)
- [발송 실패 재시도 (WAIT) 및 최종 실패 (DLQ)](#발송-실패-재시도-wait-및-최종-실패-dlq)
- [애플리케이션 시작 시 Pending 복구](#애플리케이션-시작-시-pending-복구)

---

## 알림 발송 요청 (동기)

```mermaid
sequenceDiagram
    participant C as Client
    participant API as NotificationController
    participant CMD as NotificationCommandService
    participant GR as NotificationGroupRepository
    participant OR as OutboxRepository
    participant DB as MySQL

    C->>API: POST /api/v1/notifications
    API->>CMD: request(SendCommand)
    CMD->>GR: findByClientIdAndIdempotencyKey(clientId, key)
    GR->>DB: SELECT notification_group
    DB-->>GR: existing or empty
    GR-->>CMD: Optional<NotificationGroup>

    alt 기존 멱등 요청 존재
        CMD-->>API: existingGroup
        API-->>C: 201 Created (existing groupId)
    else 신규 요청
        CMD->>GR: save(NotificationGroup + Notifications)
        GR->>DB: INSERT notification_group / notification
        DB-->>GR: savedGroup
        GR-->>CMD: savedGroup

        CMD->>OR: saveAll(Outbox events)
        OR->>DB: INSERT outbox
        DB-->>OR: saved outboxes
        OR-->>CMD: saved outboxes

        CMD-->>API: savedGroup
        API-->>C: 201 Created (groupId, totalCount)
    end
```

핵심 포인트

- 멱등 키가 유효하면 그룹 재생성 없이 기존 그룹을 반환한다.
- 신규 요청은 그룹/알림 저장과 Outbox 저장이 동일 트랜잭션에서 처리된다.

---

## Outbox 발행 (DB -> Redis)

```mermaid
sequenceDiagram
    participant S as Scheduler
    participant OP as OutboxPoller
    participant OR as OutboxRepository
    participant DB as MySQL
    participant PUB as RedisStreamPublisher
    participant RS as Redis WORK Stream

    S->>OP: pollAndPublish() every 1s
    OP->>OR: findByStatus(PENDING, 100)
    OR->>DB: SELECT outbox WHERE status=PENDING
    DB-->>OR: pendingOutboxes
    OR-->>OP: pendingOutboxes

    loop each outbox
        OP->>PUB: publish(outbox.aggregateId)
        PUB->>RS: XADD WORK {notificationId, retryCount=0}
        alt 발행 성공
            OP->>OP: outbox.markAsProcessed()
        else 발행 실패
            OP->>OP: skip delete (next poll retry)
        end
    end

    OP->>OR: deleteAll(processed)
    OR->>DB: DELETE processed outboxes
```

핵심 포인트

- 발행 성공 건만 삭제하여 최소 1회 이상(at-least-once) 전달을 보장한다.
- 발행 실패 건은 Outbox에 남겨 다음 주기에 재시도한다.

---

## WORK 소비 및 발송 성공

```mermaid
sequenceDiagram
    participant RS as Redis WORK Stream
    participant C as RedisStreamConsumer
    participant H as RedisStreamRecordHandler
    participant L as DispatchLockManager
    participant NR as NotificationRepository
    participant DS as NotificationDispatchService
    participant SA as NotificationSenderImpl
    participant SF as ChannelSenderFactory
    participant CS as ChannelSender
    participant DB as MySQL

    RS-->>C: message(notificationId, retryCount)
    C->>H: process(notificationId, retryCount)
    H->>L: tryAcquire(notificationId)

    alt 락 획득 실패
        H-->>C: return (중복 처리 방지)
        C->>RS: XACK
    else 락 획득 성공
        H->>NR: findById(notificationId)
        NR->>DB: SELECT notification
        DB-->>NR: notification
        NR-->>H: notification

        H->>DS: dispatch(notification)
        DS->>DB: UPDATE notification status=SENDING
        DS->>SA: send(notification)
        SA->>SF: getSender(channelType)
        SF->>CS: resolve sender
        CS-->>SA: SendResult.success
        SA-->>DS: success
        DS->>DB: UPDATE notification status=SENT, sent_at
        DS-->>H: DispatchResult.success

        H-->>C: success
        C->>RS: XACK
    end
```

핵심 포인트

- `notificationId` 단위 분산 락으로 다중 컨슈머 중복 발송을 방지한다.
- 성공 시 `SENT` 상태로 저장 후 WORK 메시지를 ACK한다.

---

## 발송 실패 재시도 (WAIT) 및 최종 실패 (DLQ)

```mermaid
sequenceDiagram
    participant RS as Redis WORK Stream
    participant C as RedisStreamConsumer
    participant H as RedisStreamRecordHandler
    participant DS as NotificationDispatchService
    participant W as RedisStreamWaitPublisher
    participant WS as Redis WAIT Stream
    participant SCH as RedisStreamWaitScheduler
    participant D as RedisStreamDlqPublisher
    participant DLQ as Redis DLQ Stream

    RS-->>C: message(notificationId, retryCount)
    C->>H: process(notificationId, retryCount)
    H->>DS: dispatch(notification)
    DS-->>H: DispatchResult.fail(reason)

    alt retryCount < maxRetryCount
        H-->>C: throw RetryableStreamMessageException
        C->>W: publish(notificationId, retryCount, reason)
        W->>WS: XADD WAIT {nextRetryAt, retryCount}
        C->>RS: XACK

        SCH->>WS: range(wait records)
        SCH->>SCH: due record check (now >= nextRetryAt)
        SCH->>RS: XADD WORK {retryCount + 1}
        SCH->>WS: XDEL processed wait record
    else retryCount >= maxRetryCount
        H->>DS: markAsFailed(notificationId, reason)
        H-->>C: throw NonRetryableStreamMessageException
        C->>D: publish(sourceRecordId, payload, reason)
        D->>DLQ: XADD DLQ
        C->>RS: XACK
    end
```

핵심 포인트

- 재시도 가능 오류는 WAIT 스트림으로 이동 후 지수 백오프로 재처리한다.
- 재시도 불가/한도 초과 오류는 DLQ로 이동해 운영자가 별도 대응한다.

---

## 애플리케이션 시작 시 Pending 복구

```mermaid
sequenceDiagram
    participant APP as Application Startup
    participant I as RedisStreamInitializer
    participant RS as Redis WORK Stream
    participant W as RedisStreamWaitPublisher
    participant WS as Redis WAIT Stream

    APP->>I: init()
    I->>RS: create consumer group
    Note over I,RS: BUSYGROUP 오류는 무시

    I->>RS: XPENDING group
    RS-->>I: pending messages

    loop each pending message
        I->>RS: XRANGE(recordId)
        RS-->>I: original payload
        I->>W: publish(notificationId, retryCount, "시작 시 Pending 복구")
        W->>WS: XADD WAIT
        I->>RS: XACK original record
    end
```

핵심 포인트

- 비정상 종료 등으로 남은 Pending 메시지를 기동 시점에 자동 회수한다.
- 복구 실패한 일부 메시지가 있어도 나머지 메시지 복구는 계속 진행한다.
