package com.example.infrastructure.outbox;

import java.util.List;

public record OutboxCreatedEvent(List<Long> notificationIds) {
}
