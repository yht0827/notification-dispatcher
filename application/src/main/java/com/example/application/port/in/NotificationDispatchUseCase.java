package com.example.application.port.in;

import com.example.application.port.in.result.BatchDispatchResult;
import com.example.domain.notification.Notification;

public interface NotificationDispatchUseCase {

	BatchDispatchResult dispatch(Notification notification);

	void markAsFailed(Long notificationId, String reason);
}
