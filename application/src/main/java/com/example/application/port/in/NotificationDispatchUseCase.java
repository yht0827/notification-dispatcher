package com.example.application.port.in;

import com.example.domain.notification.Notification;

public interface NotificationDispatchUseCase {

	DispatchResult dispatch(Notification notification);

	void markAsFailed(Long notificationId, String reason);

	record DispatchResult(boolean succeeded, String failReason) {

		public static DispatchResult success() {
			return new DispatchResult(true, null);
		}

		public static DispatchResult fail(String reason) {
			return new DispatchResult(false, reason);
		}

		public boolean isSuccess() {
			return succeeded;
		}

		public boolean isFailure() {
			return !succeeded;
		}
	}
}
