package com.example.application.port.out.event;

import java.util.List;

public record OutboxSavedEvent(Long groupId, List<Long> notificationIds) {
}
