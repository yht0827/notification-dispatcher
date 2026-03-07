package com.example.application.mapper;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationItemResult;
import com.example.application.port.in.result.NotificationListResult;
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
			group.getTotalCount(),
			group.getSentCount(),
			group.getFailedCount(),
			group.getPendingCount(),
			group.isCompleted(),
			group.getCreatedAt()
		);
	}

	public NotificationGroupDetailResult toGroupDetailResult(NotificationGroup group) {
		List<NotificationItemResult> notifications = group.getNotifications().stream()
			.map(this::toGroupDetailNotificationItemResult)
			.toList();

		return new NotificationGroupDetailResult(
			group.getId(),
			group.getClientId(),
			group.getSender(),
			group.getTitle(),
			group.getContent(),
			group.getGroupType(),
			group.getChannelType(),
			group.getTotalCount(),
			group.getSentCount(),
			group.getFailedCount(),
			group.getPendingCount(),
			group.isCompleted(),
			group.getCreatedAt(),
			notifications
		);
	}

	public NotificationItemResult toGroupDetailNotificationItemResult(Notification notification) {
		return new NotificationItemResult(
			notification.getId(),
			notification.getReceiver(),
			notification.getStatus(),
			notification.getSentAt(),
			notification.getFailReason(),
			notification.getCreatedAt()
		);
	}

	public NotificationResult toNotificationResult(Notification notification) {
		return toNotificationResult(notification, false);
	}

	public NotificationResult toNotificationResult(Notification notification, boolean isRead) {
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
			isRead
		);
	}

	public NotificationListResult toListResult(NotificationGroup group) {
		int moreCount = Math.max(group.getTotalCount() - 1, 0);
		return new NotificationListResult(
			group.getId(),
			group.getTitle(),
			group.getContent(),
			group.getCreatedAt(),
			group.getTotalCount(),
			moreCount
		);
	}
}
