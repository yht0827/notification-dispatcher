package com.example.application.port.in;

import com.example.application.port.in.result.NotificationDispatchResult;
import com.example.domain.notification.Notification;

public interface NotificationDispatchUseCase {

	NotificationDispatchResult dispatch(Notification notification);

	void markAsFailed(Long notificationId, String reason);
}
