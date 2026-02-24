package com.example.domain.notification;

public enum NotificationStatus {

	PENDING,  // 발송 대기
	SENDING,  // 발송 중
	SENT,     // 발송 완료
	FAILED,   // 발송 실패
	CANCELED; // 발송 취소

	public boolean canTransitionTo(NotificationStatus target) {
		return switch (this) {
			case PENDING -> target == SENDING || target == CANCELED;
			case SENDING -> target == SENT || target == FAILED;
			case SENT, FAILED, CANCELED -> false;
		};
	}

	public boolean isTerminal() {
		return this == SENT || this == FAILED || this == CANCELED;
	}
}
