package com.example.application.service.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationItemResult;
import com.example.application.port.in.result.NotificationResult;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;

@Component
public class NotificationResultMapper {

	public NotificationGroupResult toGroupResult(NotificationGroup group) {
		return new NotificationGroupResult(
			group.getId(),
			group.getClientId(),
			group.getSender(),
			group.getTitle(),
			group.getGroupType(),
			group.getChannelType(),
			group.getStats().getTotalCount(),
			group.getStats().getSentCount(),
			group.getStats().getFailedCount(),
			group.getStats().getPendingCount(),
			group.getStats().isCompleted(),
			group.getCreatedAt()
		);
	}

	public NotificationGroupDetailResult toGroupDetailResult(NotificationGroup group) {
		return toGroupDetailResult(group, Map.of());
	}

	public NotificationGroupDetailResult toGroupDetailResult(NotificationGroup group,
		Map<Long, LocalDateTime> readAtByNotificationId) {
		List<NotificationItemResult> notifications = group.getNotifications().stream()
			.map(notification -> toGroupDetailNotificationItemResult(
				notification,
				readAtByNotificationId.get(notification.getId())
			))
			.toList();

		return new NotificationGroupDetailResult(
			group.getId(),
			group.getClientId(),
			group.getSender(),
			group.getTitle(),
			group.getContent(),
			group.getGroupType(),
			group.getChannelType(),
			group.getStats().getTotalCount(),
			group.getStats().getSentCount(),
			group.getStats().getFailedCount(),
			group.getStats().getPendingCount(),
			group.getStats().isCompleted(),
			group.getCreatedAt(),
			notifications
		);
	}

	public NotificationItemResult toGroupDetailNotificationItemResult(Notification notification) {
		return toGroupDetailNotificationItemResult(notification, null);
	}

	public NotificationItemResult toGroupDetailNotificationItemResult(Notification notification, LocalDateTime readAt) {
		return new NotificationItemResult(
			notification.getId(),
			notification.getReceiver(),
			notification.getStatus(),
			notification.getSentAt(),
			notification.getFailReason(),
			notification.getCreatedAt(),
			readAt != null,
			readAt
		);
	}

	public NotificationResult toNotificationResult(Notification notification) {
		return toNotificationResult(notification, null);
	}

	public NotificationResult toNotificationResult(Notification notification, LocalDateTime readAt) {
		Optional<NotificationGroup> group = Optional.ofNullable(notification.getGroup());
		return new NotificationResult(
			notification.getId(),
			group.map(NotificationGroup::getId).orElse(null),
			notification.getReceiver(),
			group.map(NotificationGroup::getSender).orElse(null),
			group.map(NotificationGroup::getTitle).orElse(null),
			group.map(NotificationGroup::getChannelType).orElse(null),
			notification.getStatus(),
			notification.getSentAt(),
			notification.getFailReason(),
			notification.getCreatedAt(),
			readAt != null,
			readAt
		);
	}
}
