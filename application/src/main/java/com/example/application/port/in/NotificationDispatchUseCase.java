package com.example.application.port.in;

import com.example.application.port.in.result.DispatchResult;

public interface NotificationDispatchUseCase {

	DispatchResult dispatch(Long notificationId);

	void markAsFailed(Long notificationId, String reason);
}
