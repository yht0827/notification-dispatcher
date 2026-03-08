package com.example.application.port.in.result;

import java.time.LocalDateTime;

public record NotificationGroupReadResult(
	Long groupId,
	int readCount,
	LocalDateTime readAt
) {
}
