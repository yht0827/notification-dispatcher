package com.example.application.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.out.NotificationRepository;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.NotificationSender.SendResult;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

	private static final int MAX_RETRY_ATTEMPTS = 3;
	private static final int RETRY_DELAY_MINUTES = 5;

	private final NotificationRepository notificationRepository;
	private final NotificationSender notificationSender;

	@Transactional
	public void dispatch(Notification notification) {
		notification.startSending();

		SendResult result = notificationSender.send(notification);

		if (result.isSuccess()) {
			notification.markAsSent();
			log.info("알림 발송 성공: id={}, receiver={}", notification.getId(), notification.getReceiver());
		} else {
			handleFailure(notification, result.failReason());
		}

		notificationRepository.save(notification);
	}

	private void handleFailure(Notification notification, String reason) {
		if (notification.canRetry(MAX_RETRY_ATTEMPTS)) {
			LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES);
			notification.markAsRetryWait(nextRetryAt);
			log.warn("알림 발송 실패, 재시도 예정: id={}, attempt={}, nextRetry={}",
				notification.getId(), notification.getAttemptCount(), nextRetryAt);
		} else {
			notification.markAsFailed(reason);
			log.error("알림 발송 최종 실패: id={}, reason={}",
				notification.getId(), reason);
		}
	}
}
