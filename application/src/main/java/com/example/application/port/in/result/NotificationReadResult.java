package com.example.application.port.in.result;

import java.time.LocalDateTime;

public record NotificationReadResult(
	Long notificationId,
	LocalDateTime readAt
) {
}
