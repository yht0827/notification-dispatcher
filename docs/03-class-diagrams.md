# 클래스 다이어그램

> Notification Dispatcher의 계층 구조와 핵심 클래스 관계

## 목차

- [레이어드 구조 (Hexagonal)](#레이어드-구조-hexagonal)
- [도메인 모델](#도메인-모델)
- [비동기 메시징 처리](#비동기-메시징-처리)
- [채널 발송 전략](#채널-발송-전략)
- [핵심 클래스 책임 요약](#핵심-클래스-책임-요약)

---

## 레이어드 구조 (Hexagonal)

```mermaid
classDiagram
    class NotificationController {
      +send(request)
      +markAsRead(notificationId)
      +getGroup(groupId)
      +getGroupsByClientId(clientId)
      +getNotification(notificationId)
    }

    class NotificationWriteUseCase {
      <<interface>>
      +request(command) NotificationGroup
      +markAsRead(notificationId) boolean
    }

    class NotificationQueryUseCase {
      <<interface>>
      +getGroupDetail(groupId) Optional~NotificationGroup~
      +getNotification(notificationId) Optional~Notification~
    }

    class NotificationDispatchUseCase {
      <<interface>>
      +dispatch(notification) NotificationDispatchResult
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

    class RabbitMQConsumer {
      +onMessage(payload, message, channel, deliveryTag)
    }

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

    class NotificationMessagePayload {
      +notificationId
      +retryCount
    }

    class NotificationWaitPayload {
      +notificationId
      +currentRetryCount
      +nextRetryCount
      +delayMillis
      +lastError
    }

    class NotificationDeadLetterPayload {
      +recordId
      +notificationId
      +payload
      +reason
      +failedAt
    }

    OutboxPoller --> RabbitMQPublisher

    RabbitMQConsumer --> RabbitMQRecordHandler
    RabbitMQConsumer --> RabbitMQWaitPublisher
    RabbitMQConsumer --> RabbitMQDlqPublisher

    RabbitMQRecordHandler --> NotificationDispatchUseCase
    RabbitMQRecordHandler --> NotificationRepository
    RabbitMQRecordHandler --> DispatchLockManager
    RabbitMQRecordHandler --> NotificationRabbitProperties

    RabbitMQWaitPublisher --> NotificationWaitPayload
    RabbitMQDlqPublisher --> NotificationDeadLetterPayload
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

## 핵심 클래스 책임 요약

| 클래스 | 레이어 | 주요 책임 |
|-------|--------|-----------|
| `NotificationController` | API | 요청 검증/DTO 변환/응답 생성 |
| `NotificationWriteService` | Application | 멱등성 검사, 그룹 생성, Outbox 저장 |
| `NotificationQueryService` | Application | 그룹/알림 조회, 커서 페이지 계산 |
| `NotificationDispatchService` | Application | 발송 상태 전이, 채널 발송 위임 |
| `OutboxPoller` | Infrastructure | Outbox -> WORK 큐 발행 |
| `RabbitMQConsumer` | Infrastructure | WORK 메시지 소비 및 ACK 제어 |
| `RabbitMQRecordHandler` | Infrastructure | 분산 락/재시도 분기/실패 처리 |
| `NotificationRecoveryPoller` | Infrastructure | 장시간 PENDING 알림 재발행 |
| `NotificationSenderImpl` | Infrastructure | 채널별 Sender 전략 선택 |
| `DispatchLockManagerImpl` | Infrastructure | notificationId 단위 락 획득/해제 |
