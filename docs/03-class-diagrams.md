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
      +getGroupsByClientId(clientId, request)
      +getNotificationsByReceiver(clientId, request)
      +getUnreadCount(clientId, receiver)
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
      +getGroupsByClientId(clientId, cursorId, size, completed) CursorSlice
      +getNotificationsByReceiver(clientId, receiver, cursorId, size) CursorSlice
      +getNotification(notificationId) Optional~NotificationResult~
      +getUnreadCount(clientId, receiver) NotificationUnreadCountResult
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

    class RabbitMQSingleConsumer {
      +onMessage(message, channel)
    }

    class DeadLetterPublisher {
      <<interface>>
      +publish(sourceRecordId, payload, notificationId, reason)
    }

    class WaitPublisher {
      <<interface>>
      +publish(notificationId, retryCount, lastError)
      +publish(notificationId, retryCount, lastError, retryDelayMillis)
    }

    class RabbitMQDlqPublisher {
      +publish(sourceRecordId, payload, notificationId, reason)
    }

    class RabbitMQWaitPublisher {
      +publish(notificationId, retryCount, lastError, retryDelayMillis)
    }

    class RabbitMQRecordHandler {
      +processBatch(requests) List~RecordProcessResult~
    }

    class NotificationRecoveryPoller {
      +recoverStuckNotifications()
    }

    class NotificationRabbitProperties {
      +resolveMaxRetryCount()
      +calculateRetryDelayMillis(retryCount, overrideMillis)
    }

    OutboxPoller --> RabbitMQPublisher

    RabbitMQSingleConsumer --> RabbitMQRecordHandler
    RabbitMQSingleConsumer --> DeadLetterPublisher
    RabbitMQSingleConsumer --> WaitPublisher

    DeadLetterPublisher <|.. RabbitMQDlqPublisher
    WaitPublisher <|.. RabbitMQWaitPublisher

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
      +archiveExpiredData()
      +managePartitions()
    }

    class NotificationArchiveService {
      +archiveExpiredData() ArchiveRunResult
      -archiveNotificationBatch(cutoff) int
      -archiveCompletedGroupBatch(cutoff) int
    }

    class NotificationPartitionManager {
      +ensureNextMonthPartitions()
      +dropOldPartitions()
    }

    class ArchiveStorage {
      <<interface>>
      +export(tableName, partitionName)
    }

    class NoOpArchiveStorage {
      +export(tableName, partitionName)
    }

    class ArchiveRunResult {
      <<record>>
      +cutoff LocalDateTime
      +archivedNotifications int
      +archivedGroups int
      +hasWork() boolean
    }

    class ArchiveProperties {
      +resolveRetentionDays() int
      +resolveBatchSize() int
      +resolvePartitionRetentionMonths() int
    }

    NotificationArchiveScheduler --> NotificationArchiveService
    NotificationArchiveScheduler --> NotificationPartitionManager

    NotificationArchiveService --> ArchiveRunResult
    NotificationArchiveService --> ArchiveProperties

    NotificationPartitionManager --> ArchiveStorage
    NotificationPartitionManager --> ArchiveProperties

    ArchiveStorage <|.. NoOpArchiveStorage
```

---

## 캐시 및 데이터소스

```mermaid
classDiagram
    class AdminNotificationController {
      +getStats()
      +getStatsByClientId(clientId)
    }

    class AdminNotificationStatsUseCase {
      <<interface>>
      +getStats() NotificationStatsResult
      +getStatsByClientId(clientId) NotificationStatsResult
    }

    class RedisStatsCache {
      +get(key, loader) NotificationStatsResult
      +evict(key)
      +evictAll()
    }

    class DataSourceRoutingConfig {
      <<ConditionalOnProperty routing.enabled=true>>
      +masterDataSource() DataSource
      +slaveDataSource() DataSource
      +dataSource() DataSource
    }

    class RoutingDataSource {
      +determineCurrentLookupKey() Object
    }

    AdminNotificationController --> AdminNotificationStatsUseCase
    AdminNotificationStatsUseCase --> RedisStatsCache
    DataSourceRoutingConfig --> RoutingDataSource
```

---

## 핵심 클래스 책임 요약

| 클래스 | 레이어 | 주요 책임 |
|-------|--------|-----------|
| `NotificationController` | API | 요청 검증/DTO 변환/응답 생성 |
| `AdminNotificationController` | API | 관리자 통계 API (전체/클라이언트별) |
| `NotificationWriteService` | Application | 멱등성 검사, 그룹 생성, Outbox 저장, 읽음 처리 |
| `NotificationQueryService` | Application | 그룹/알림/수신자별 조회, 커서 페이지 계산 |
| `NotificationDispatchService` | Application | 발송 상태 전이, 채널 발송 위임, 배치 발송 |
| `OutboxPoller` | Infrastructure | Outbox → WORK 큐 발행 |
| `RabbitMQSingleConsumer` | Infrastructure | WORK 메시지 단건 소비, 유효성 검사, DLQ/WAIT 분기 |
| `RabbitMQRecordHandler` | Infrastructure | 분산 락 획득, 발송 위임, 재시도/실패 결과 반환 |
| `NotificationRecoveryPoller` | Infrastructure | 장시간 PENDING 알림 재발행 |
| `NotificationSenderImpl` | Infrastructure | 채널별 Sender 전략 선택 |
| `DispatchLockManagerImpl` | Infrastructure | notificationId 단위 락 획득/해제 |
| `RedisStatsCache` | Infrastructure | 관리자 통계 Redis 캐시 (cache.stats.enabled) |
| `NotificationArchiveService` | Infrastructure | 만료 알림 archive 테이블 이관 |
| `NotificationPartitionManager` | Infrastructure | 월별 파티션 생성 및 오래된 파티션 삭제 |
| `NotificationArchiveScheduler` | Infrastructure | 아카이브 배치 및 파티션 관리 스케줄 실행 |
