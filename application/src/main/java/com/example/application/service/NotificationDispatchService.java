package com.example.application.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.DispatchResult;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.SendResult;
import com.example.application.port.out.event.AdminStatsChangedEvent;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.exception.UnsupportedChannelException;
import com.example.domain.notification.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService implements NotificationDispatchUseCase {

	private final NotificationRepository notificationRepository;
	private final NotificationSender notificationSender;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	@Transactional
	public DispatchResult dispatch(Long notificationId) {
		Notification notification = notificationRepository.findById(notificationId).orElse(null);
		if (notification == null) {
			return DispatchResult.failNonRetryable(notificationId, "알림을 찾을 수 없음: " + notificationId);
		}
		// 이미 처리 완료(SENT/FAILED)된 알림은 재발송 없이 success 반환
		if (notification.isTerminal()) {
			return DispatchResult.success(notification.getId());
		}

		// 1단계: PENDING → SENDING 상태 전환
		notification.startSending();

		// 2단계: 외부 API 호출
		SendResult sendResult = sendNotification(notification);

		// 3단계: 결과에 따라 SENT/FAILED 상태 반영
		if (sendResult.isSuccess()) {
			notification.markAsSent();
			eventPublisher.publishEvent(new AdminStatsChangedEvent());
			return DispatchResult.success(notification.getId());
		} else if (sendResult.isNonRetryableFailure()) {
			notification.markAsFailed(sendResult.failReason());
			eventPublisher.publishEvent(new AdminStatsChangedEvent());
			return DispatchResult.failNonRetryable(notification.getId(), sendResult.failReason());
		} else {
			return DispatchResult.failRetryable(notification.getId(), sendResult.failReason(),
				sendResult.retryDelayMillis());
		}
	}

	@Override
	@Transactional
	public void markAsFailed(Long notificationId, String reason) {
		notificationRepository.findById(notificationId).ifPresent(notification -> {
			notification.markAsFailed(reason);
			notificationRepository.save(notification);
			eventPublisher.publishEvent(new AdminStatsChangedEvent());
			log.error("알림 최종 실패: id={}, reason={}", notificationId, reason);
		});
	}

	private SendResult sendNotification(Notification notification) {
		try {
			return notificationSender.send(notification);
		} catch (UnsupportedChannelException e) {
			return SendResult.failNonRetryable(e.getMessage());
		} catch (RuntimeException e) {
			return SendResult.fail(e.getMessage());
		}
	}
}
