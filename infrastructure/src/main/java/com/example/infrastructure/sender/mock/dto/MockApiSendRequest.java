package com.example.infrastructure.sender.mock.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;

public record MockApiSendRequest(
	String requestId,
	String channelType,
	String receiver,
	String message,
	Map<String, Object> metadata
) {
	public static MockApiSendRequest from(Notification notification, ChannelType channelType) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("notificationId", notification.getId());
		metadata.put("groupId", notification.getGroup() == null ? null : notification.getGroup().getId());
		metadata.put("sender", notification.getGroup() == null ? null : notification.getGroup().getSender());
		metadata.put("title", notification.getGroup() == null ? null : notification.getGroup().getTitle());
		metadata.put("attemptCount", notification.getAttemptCount());

		String requestId = notification.getId() == null
			? "notification-unknown"
			: "notification-" + notification.getId();

		return new MockApiSendRequest(
			requestId,
			channelType.name(),
			notification.getReceiver(),
			notification.getGroup() == null ? "" : notification.getGroup().getContent(),
			metadata
		);
	}
}
