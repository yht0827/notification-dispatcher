package com.example.application.port.in;

import java.util.List;
import java.util.Optional;

import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;

public interface NotificationQueryUseCase {

	Optional<NotificationGroup> getGroup(Long groupId);

	List<NotificationGroup> getGroupsByClientId(String clientId);

	Optional<Notification> getNotification(Long notificationId);

	List<Notification> getNotificationsByReceiver(String receiver);
}
