package com.example.application.service.event;

import java.util.List;

/**
 * Outbox 저장 완료 이벤트.
 * 트랜잭션 커밋 후 Stream 발행을 트리거합니다.
 */
public record OutboxSavedEvent(List<Long> notificationIds) {
}
