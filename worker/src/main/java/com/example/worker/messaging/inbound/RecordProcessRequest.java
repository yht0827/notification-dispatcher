package com.example.worker.messaging.inbound;

public record RecordProcessRequest(long contextId, Long notificationId, int retryCount) {
}
