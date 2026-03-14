package com.example.application.port.out.event;

import java.util.List;

public record SyncDispatchEvent(List<Long> notificationIds) {
}
