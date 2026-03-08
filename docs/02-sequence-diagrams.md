# 시퀀스 다이어그램

> Notification Dispatcher 주요 런타임 흐름

## 목차

- [알림 발송 요청 (동기)](#알림-발송-요청-동기)
- [Outbox 발행 (DB -> RabbitMQ)](#outbox-발행-db---rabbitmq)
- [WORK 소비 및 발송 성공](#work-소비-및-발송-성공)
- [발송 실패 재시도 (WAIT) 및 최종 실패 (DLQ)](#발송-실패-재시도-wait-및-최종-실패-dlq)
- [알림 읽음 처리](#알림-읽음-처리)
- [아카이브 배치](#아카이브-배치)
- [애플리케이션 시작 시 Pending 복구](#애플리케이션-시작-시-pending-복구)

---

## 알림 발송 요청 (동기)

```mermaid
sequenceDiagram
    participant C as Client
    participant API as NotificationController
    participant CMD as NotificationWriteService
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

## Outbox 발행 (DB -> RabbitMQ)

```mermaid
sequenceDiagram
    participant S as Scheduler
    participant OP as OutboxPoller
    participant OR as OutboxRepository
    participant DB as MySQL
    participant PUB as RabbitMQPublisher
    participant RS as RabbitMQ WORK Queue

    S->>OP: pollAndPublish() every 1s
    OP->>OR: findByStatus(PENDING, 100)
    OR->>DB: SELECT outbox WHERE status=PENDING ORDER BY created_at ASC
    DB-->>OR: pendingOutboxes
    OR-->>OP: pendingOutboxes

    loop each outbox
        OP->>PUB: publish(outbox.aggregateId)
        PUB->>RS: WORK 큐 메시지 발행 {notificationId, retryCount=0}
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

`batch-listener-enabled=false` (기본) 기준. 배치 모드(`RabbitMQBatchConsumer`)도 동일한 `MessageProcessOrchestrator`를 통해 처리한다.

```mermaid
sequenceDiagram
    participant RS as RabbitMQ WORK Queue
    participant C as RabbitMQConsumer
    participant O as MessageProcessOrchestrator
    participant H as RabbitMQRecordHandler
    participant L as DispatchLockManager
    participant NR as NotificationRepository
    participant DS as NotificationDispatchService
    participant SA as NotificationSenderImpl
    participant CS as ChannelSender
    participant DB as MySQL

    RS-->>C: message(notificationId, retryCount)
    C->>O: process(MessageProcessContext)
    O->>O: validate payload

    alt 유효하지 않은 메시지
        O->>O: publishToDeadLetter
        O-->>C: ack
    else 유효한 메시지
        O->>H: process(notificationId, retryCount)
        H->>L: tryAcquire(notificationId)

        alt 락 획득 실패
            H-->>O: return (중복 처리 방지)
            O-->>C: ack
        else 락 획득 성공
            H->>NR: findById(notificationId)
            NR->>DB: SELECT notification
            DB-->>NR: notification
            NR-->>H: notification

            H->>DS: dispatch(notification)
            DS->>DB: UPDATE status=SENDING
            DS->>SA: send(notification)
            SA->>CS: channelType 기반 Sender 선택 및 호출
            CS-->>SA: SendResult.success
            SA-->>DS: success
            DS->>DB: UPDATE status=SENT, sent_at
            DS-->>H: DispatchResult.success

            H-->>O: success
            O-->>C: ack
            C->>RS: basicAck
        end
    end
```

핵심 포인트

- `MessageProcessOrchestrator`가 유효성 검사, 분기, DLQ 전송을 담당한다.
- `notificationId` 단위 분산 락으로 다중 컨슈머 중복 발송을 방지한다.
- 성공 시 `SENT` 상태로 저장 후 WORK 메시지를 ACK한다.

---

## 발송 실패 재시도 (WAIT) 및 최종 실패 (DLQ)

```mermaid
sequenceDiagram
    participant RS as RabbitMQ WORK Queue
    participant C as RabbitMQConsumer
    participant O as MessageProcessOrchestrator
    participant H as RabbitMQRecordHandler
    participant DS as NotificationDispatchService
    participant W as RabbitMQWaitPublisher
    participant WS as RabbitMQ WAIT Queue
    participant B as RabbitMQ Broker (TTL + DLX)
    participant D as RabbitMQDlqPublisher
    participant DLQ as RabbitMQ DLQ Queue

    RS-->>C: message(notificationId, retryCount)
    C->>O: process(MessageProcessContext)
    O->>H: process(notificationId, retryCount)
    H->>DS: dispatch(notification)
    DS-->>H: DispatchResult.fail(reason)

    alt retryCount < maxRetryCount
        H-->>O: throw RetryableMessageException
        O->>W: publish(notificationId, retryCount, reason)
        W->>WS: WAIT 큐 발행 {expiration=delay, retryCount}
        O-->>C: ack
        C->>RS: basicAck

        WS->>B: TTL 만료
        B->>RS: DLX 라우팅으로 WORK 재진입 {retryCount+1}
    else retryCount >= maxRetryCount
        H->>DS: markAsFailed(notificationId, reason)
        H-->>O: throw NonRetryableMessageException
        O->>D: publish(sourceRecordId, payload, reason)
        D->>DLQ: DLQ 큐 발행
        O-->>C: ack
        C->>RS: basicAck
    end
```

핵심 포인트

- 재시도 가능 오류는 WAIT 큐(TTL + DLX)로 이동 후 WORK 큐로 자동 재진입한다.
- 429 Rate Limit 응답 시 `Retry-After` 헤더 값을 WAIT TTL로 사용한다.
- 재시도 불가/한도 초과 오류는 DLQ로 이동해 운영자가 별도 대응한다.

---

## 알림 읽음 처리

```mermaid
sequenceDiagram
    participant C as Client
    participant API as NotificationController
    participant WS as NotificationWriteService
    participant NR as NotificationRepository
    participant RSR as NotificationReadStatusRepository
    participant DB as MySQL

    C->>API: PATCH /api/v1/notifications/{notificationId}/read
    API->>WS: markAsRead(notificationId)
    WS->>NR: findById(notificationId)
    NR->>DB: SELECT notification
    DB-->>NR: notification or empty

    alt 알림 미존재
        WS-->>API: Optional.empty
        API-->>C: 404 NOT FOUND
    else 알림 존재
        WS->>RSR: findById(notificationId)
        RSR->>DB: SELECT notification_read_status

        alt 이미 읽음
            RSR-->>WS: existing readStatus
            WS-->>API: NotificationReadResult(existing readAt)
        else 미읽음
            WS->>RSR: save(notificationId, now)
            RSR->>DB: INSERT notification_read_status
            WS-->>API: NotificationReadResult(readAt)
        end

        API-->>C: 200 OK {notificationId, readAt}
    end
```

핵심 포인트

- 읽음 상태는 `Notification` 엔티티가 아닌 별도 테이블 `notification_read_status`에서 관리한다.
- 이미 읽은 알림을 다시 읽음 처리해도 기존 `readAt`을 반환한다 (멱등).
- 그룹 읽음 처리(`PATCH /groups/{groupId}/read`)도 동일 패턴으로 그룹 내 알림을 일괄 처리한다.

---

## 아카이브 배치

```mermaid
sequenceDiagram
    participant S as Scheduler
    participant AS as NotificationArchiveService
    participant DB as MySQL

    S->>AS: archiveExpiredData() (매일)
    AS->>AS: cutoff = now() - retentionDays(7)

    loop notification 배치 (0건 될 때까지)
        AS->>DB: SELECT id FROM notification WHERE created_at < cutoff AND status IN (SENT,FAILED,CANCELED) LIMIT batchSize
        AS->>DB: INSERT INTO notification_read_status_archive SELECT ... (FK 먼저)
        AS->>DB: DELETE FROM notification_read_status WHERE notification_id IN (...)
        AS->>DB: INSERT INTO notification_archive SELECT ...
        AS->>DB: DELETE FROM notification WHERE id IN (...)
    end

    loop group 배치 (0건 될 때까지)
        AS->>DB: SELECT group_id FROM notification_group WHERE 연결된 notification 없음 AND created_at < cutoff
        AS->>DB: INSERT INTO notification_group_archive SELECT ...
        AS->>DB: DELETE FROM notification_group WHERE id IN (...)
    end

    AS-->>S: ArchiveRunResult(cutoff, archivedNotifications, archivedGroups)
```

핵심 포인트

- notification 먼저, group은 그 다음 (notification이 모두 없어진 그룹만 이관 가능).
- `notification_read_status` → `notification` 순서로 삭제 (FK 제약 준수).
- 각 단계는 `TransactionTemplate`으로 배치 단위 트랜잭션 처리.

---

## 애플리케이션 시작 시 Pending 복구

```mermaid
sequenceDiagram
    participant APP as Application Startup
    participant RP as NotificationRecoveryPoller
    participant NR as NotificationRepository
    participant DB as MySQL
    participant PUB as NotificationEventPublisher
    participant MQ as RabbitMQ WORK Queue

    APP->>RP: recoverStuckNotifications()
    RP->>NR: findByStatusAndCreatedAtBefore(PENDING, threshold, batchSize)
    NR->>DB: SELECT pending notifications
    DB-->>NR: stuck notifications
    NR-->>RP: stuck notifications

    loop each pending notification
        RP->>PUB: publish(notificationId)
        PUB->>MQ: WORK 큐 발행
    end
```

핵심 포인트

- 장시간 PENDING 상태 알림을 주기적으로 회수해 WORK 큐로 재발행한다.
- 일부 복구 실패가 발생해도 나머지 알림 복구는 계속 진행한다.
