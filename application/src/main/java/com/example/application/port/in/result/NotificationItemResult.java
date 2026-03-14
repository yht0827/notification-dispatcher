package com.example.application.port.in.result;

import java.time.LocalDateTime;

import com.example.domain.notification.NotificationStatus;

public record NotificationItemResult(
	Long notificationId,
	String receiver,
	NotificationStatus status,
	LocalDateTime sentAt,
	String failReason,
	LocalDateTime createdAt,
	boolean isRead,
	LocalDateTime readAt
) {
}
