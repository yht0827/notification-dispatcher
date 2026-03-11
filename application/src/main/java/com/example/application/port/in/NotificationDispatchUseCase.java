package com.example.application.port.in;

import java.util.List;

import com.example.application.port.in.result.BatchDispatchResult;
import com.example.domain.notification.Notification;

public interface NotificationDispatchUseCase {

	List<BatchDispatchResult> dispatchBatch(List<Notification> notifications);

	void markAsFailed(Long notificationId, String reason);
}
