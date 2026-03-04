package com.example.application.port.in.result;

import java.time.LocalDateTime;

public record NotificationListResult(
	Long groupId,
	String title,
	String content,
	LocalDateTime createdAt,
	int totalCount,
	int moreCount
) {
}
