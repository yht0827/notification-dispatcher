package com.example.application.port.in.result;

import java.time.LocalDateTime;

import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationStatus;

public record NotificationResult(
	Long id,
	Long groupId,
	String receiver,
	String sender,
	String title,
	ChannelType channelType,
	NotificationStatus status,
	LocalDateTime sentAt,
	String failReason,
	LocalDateTime createdAt,
	boolean isRead,
	LocalDateTime readAt
) {
}
