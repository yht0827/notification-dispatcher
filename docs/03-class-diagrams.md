# 클래스 다이어그램

> Notification Dispatcher의 계층 구조와 핵심 클래스 관계

## 목차

- [레이어드 구조 (Hexagonal)](#레이어드-구조-hexagonal)
- [도메인 모델](#도메인-모델)
- [비동기 메시징 처리](#비동기-메시징-처리)
- [채널 발송 전략](#채널-발송-전략)
- [아카이브](#아카이브)
- [핵심 클래스 책임 요약](#핵심-클래스-책임-요약)

---

## 레이어드 구조 (Hexagonal)

```mermaid
classDiagram
    class NotificationController {
      +send(request)
      +getNotification(notificationId)
      +markAsRead(notificationId)
      +getGroup(groupId)
      +getGroupsByClientId(clientId)
      +markGroupAsRead(groupId)
    }

    class NotificationWriteUseCase {
      <<interface>>
      +request(command) NotificationCommandResult
      +markAsRead(notificationId) Optional~NotificationReadResult~
      +markGroupAsRead(groupId) Optional~NotificationGroupReadResult~
    }

    class NotificationQueryUseCase {
      <<interface>>
      +getGroup(groupId) Optional~NotificationGroupResult~
      +getGroupDetail(groupId) Optional~NotificationGroupDetailResult~
      +getGroupsByClientId(clientId, cursorId, size) CursorSlice
      +getNotification(notificationId) Optional~NotificationResult~
    }

    class NotificationDispatchUseCase {
      <<interface>>
      +dispatch(notification) NotificationDispatchResult
      +dispatchBatch(notifications) BatchDispatchResult
      +markAsFailed(notificationId, reason)
    }

    class NotificationWriteService
    class NotificationQueryService
    class NotificationDispatchService

    class NotificationGroupRepository {
      <<interface>>
    }

    class NotificationRepository {
      <<interface>>
    }

    class OutboxRepository {
      <<interface>>
    }

    class NotificationReadStatusRepository {
      <<interface>>
      +findById(notificationId)
      +save(notificationId, readAt)
      +saveAll(notificationIds, readAt)
    }

    class NotificationSender {
      <<interface>>
      +send(notification) SendResult
    }

    class DispatchLockManager {
      <<interface>>
      +tryAcquire(notificationId) boolean
      +release(notificationId)
    }

    NotificationController --> NotificationWriteUseCase
    NotificationController --> NotificationQueryUseCase

    NotificationWriteUseCase <|.. NotificationWriteService
    NotificationQueryUseCase <|.. NotificationQueryService
    NotificationDispatchUseCase <|.. NotificationDispatchService

    NotificationWriteService --> NotificationGroupRepository
    NotificationWriteService --> OutboxRepository
    NotificationWriteService --> NotificationReadStatusRepository
    NotificationQueryService --> NotificationGroupRepository
    NotificationQueryService --> NotificationRepository
    NotificationDispatchService --> NotificationRepository
    NotificationDispatchService --> NotificationSender
```

---

## 도메인 모델

```mermaid
classDiagram
    class BaseEntity {
      +createdAt
      +updatedAt
    }

    class NotificationGroup {
      +id
      +clientId
      +idempotencyKey
      +sender
      +title
      +content
      +groupType
      +channelType
      +totalCount
      +sentCount
      +failedCount
      +addNotification(receiver)
      +incrementSentCount()
      +incrementFailedCount()
      +getPendingCount() int
      +isCompleted() boolean
    }

    class Notification {
      +id
      +version
      +receiver
      +status
      +attemptCount
      +sentAt
      +failReason
      +startSending()
      +markAsSent()
      +markAsFailed(reason)
      +cancel()
      +isTerminal() boolean
    }

    class Outbox {
      +id
      +aggregateType
      +aggregateId
      +eventType
      +payload
      +status
      +processedAt
      +markAsProcessed()
      +isPending()
    }

    class NotificationStatus {
      <<enumeration>>
      PENDING
      SENDING
      SENT
      FAILED
      CANCELED
    }

    class GroupType {
      <<enumeration>>
      SINGLE
      BULK
    }

    class ChannelType {
      <<enumeration>>
      EMAIL
      SMS
      KAKAO
    }

    class OutboxStatus {
      <<enumeration>>
      PENDING
      PROCESSED
      FAILED
    }

    BaseEntity <|-- NotificationGroup
    BaseEntity <|-- Notification
    BaseEntity <|-- Outbox

    NotificationGroup "1" o-- "*" Notification : notifications
    Notification --> NotificationStatus
    NotificationGroup --> GroupType
    NotificationGroup --> ChannelType
    Outbox --> OutboxStatus
```

---

## 비동기 메시징 처리

```mermaid
classDiagram
    class OutboxPoller {
      +pollAndPublish()
    }

    class RabbitMQPublisher {
      +publish(notificationId)
    }

    class MessageProcessOrchestrator {
      +process(context) MessageProcessDecision
      -publishToDeadLetter(...)
    }

    class RabbitMQConsumer {
      +onMessage(payload, message, channel, deliveryTag)
    }

    class RabbitMQBatchConsumer {
      +onMessage(payloads, messages, channel, deliveryTags)
    }

    note for RabbitMQConsumer "batch-listener-enabled=false"
    note for RabbitMQBatchConsumer "batch-listener-enabled=true"

    class RabbitMQRecordHandler {
      +process(notificationId, retryCount)
    }

    class NotificationRecoveryPoller {
      +recoverStuckNotifications()
    }

    class RabbitMQWaitPublisher {
      +publish(notificationId, retryCount, lastError)
    }

    class RabbitMQDlqPublisher {
      +publish(sourceRecordId, payload, notificationId, reason)
    }

    class NotificationRabbitProperties {
      +resolveMaxRetryCount()
      +calculateRetryDelayMillis(retryCount)
    }

    OutboxPoller --> RabbitMQPublisher

    RabbitMQConsumer --> MessageProcessOrchestrator
    RabbitMQBatchConsumer --> MessageProcessOrchestrator

    MessageProcessOrchestrator --> RabbitMQRecordHandler
    MessageProcessOrchestrator --> RabbitMQWaitPublisher
    MessageProcessOrchestrator --> RabbitMQDlqPublisher

    RabbitMQRecordHandler --> NotificationDispatchUseCase
    RabbitMQRecordHandler --> NotificationRepository
    RabbitMQRecordHandler --> DispatchLockManager
    RabbitMQRecordHandler --> NotificationRabbitProperties

    NotificationRecoveryPoller --> NotificationRepository
    NotificationRecoveryPoller --> RabbitMQPublisher
```

---

## 채널 발송 전략

```mermaid
classDiagram
    class NotificationSender {
      <<interface>>
      +send(notification) SendResult
    }

    class NotificationSenderImpl {
      +send(notification) SendResult
    }

    class ChannelSenderFactory {
      +getSender(channelType) ChannelSender
    }

    class ChannelSender {
      <<interface>>
      +getChannelType() ChannelType
      +send(notification) SendResult
    }

    class EmailSender
    class SmsSender
    class KakaoSender

    NotificationSender <|.. NotificationSenderImpl
    NotificationSenderImpl --> ChannelSenderFactory

    ChannelSender <|.. EmailSender
    ChannelSender <|.. SmsSender
    ChannelSender <|.. KakaoSender

    ChannelSenderFactory --> ChannelSender : registry
```

---

## 아카이브

```mermaid
classDiagram
    class NotificationArchiveScheduler {
      +runArchive()
    }

    class NotificationArchiveStartupRunner {
      +run(args)
    }

    class NotificationArchiveService {
      +archiveExpiredData(cutoff) ArchiveRunResult
      +ensureNextMonthPartitions()
      -archiveNotificationBatch(cutoff) int
      -archiveCompletedGroupBatch(cutoff) int
    }

    class ArchiveRunResult {
      <<record>>
      +cutoff LocalDateTime
      +archivedNotifications int
      +archivedGroups int
      +hasWork() boolean
    }

    class ArchiveProperties {
      +retentionDays int
      +batchSize int
      +cronExpression String
    }

    NotificationArchiveScheduler --> NotificationArchiveService
    NotificationArchiveStartupRunner --> NotificationArchiveService
    NotificationArchiveService --> ArchiveRunResult
    NotificationArchiveService --> ArchiveProperties
```

---

## 핵심 클래스 책임 요약

| 클래스 | 레이어 | 주요 책임 |
|-------|--------|-----------|
| `NotificationController` | API | 요청 검증/DTO 변환/응답 생성 |
| `NotificationWriteService` | Application | 멱등성 검사, 그룹 생성, Outbox 저장, 읽음 처리 |
| `NotificationQueryService` | Application | 그룹/알림 조회, 커서 페이지 계산 |
| `NotificationDispatchService` | Application | 발송 상태 전이, 채널 발송 위임, 배치 발송 |
| `OutboxPoller` | Infrastructure | Outbox → WORK 큐 발행 |
| `RabbitMQConsumer` | Infrastructure | WORK 메시지 단건 소비 (기본 모드) |
| `RabbitMQBatchConsumer` | Infrastructure | WORK 메시지 배치 소비 (배치 모드) |
| `MessageProcessOrchestrator` | Infrastructure | 유효성 검사, 분기, DLQ 전송 공통 처리 |
| `RabbitMQRecordHandler` | Infrastructure | 분산 락/재시도 분기/실패 처리 |
| `NotificationRecoveryPoller` | Infrastructure | 장시간 PENDING 알림 재발행 |
| `NotificationSenderImpl` | Infrastructure | 채널별 Sender 전략 선택 |
| `DispatchLockManagerImpl` | Infrastructure | notificationId 단위 락 획득/해제 |
| `NotificationArchiveService` | Infrastructure | 만료 알림 archive 테이블 이관 및 파티션 관리 |
| `NotificationArchiveScheduler` | Infrastructure | 아카이브 배치 스케줄 실행 |
| `NotificationArchiveStartupRunner` | Infrastructure | 앱 시작 시 다음 달 파티션 사전 생성 |
