package com.example.application.port.in;

import java.util.List;
import java.util.Optional;

import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;

public interface NotificationQueryUseCase {

	Optional<NotificationGroup> getGroup(Long groupId);

	Optional<NotificationGroup> getGroupDetail(Long groupId);

	NotificationGroupSlice getRecentGroups(Long cursorId, int size);

	NotificationGroupSlice getGroupsByClientId(String clientId, Long cursorId, int size);

	Optional<Notification> getNotification(Long notificationId);

	List<Notification> getNotificationsByReceiver(String receiver);
}
