package com.example.application.port.in.result;

import java.time.LocalDateTime;
import java.util.List;

import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;

public record NotificationGroupDetailResult(
	Long groupId,
	String clientId,
	String sender,
	String title,
	String content,
	GroupType groupType,
	ChannelType channelType,
	int totalCount,
	int sentCount,
	int failedCount,
	int pendingCount,
	boolean completed,
	LocalDateTime createdAt,
	List<NotificationItemResult> notifications
) {
}
