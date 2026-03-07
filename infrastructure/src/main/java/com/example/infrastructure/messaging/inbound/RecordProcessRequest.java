package com.example.infrastructure.messaging.inbound;

public record RecordProcessRequest(long contextId, Long notificationId, int retryCount) {
}
