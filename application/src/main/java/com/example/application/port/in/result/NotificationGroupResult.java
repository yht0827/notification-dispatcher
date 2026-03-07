package com.example.application.port.in.result;

import java.time.LocalDateTime;

import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;

public record NotificationGroupResult(
	Long id,
	String clientId,
	String sender,
	String title,
	GroupType groupType,
	ChannelType channelType,
	int totalCount,
	int sentCount,
	int failedCount,
	int pendingCount,
	boolean completed,
	LocalDateTime createdAt
) {
}
