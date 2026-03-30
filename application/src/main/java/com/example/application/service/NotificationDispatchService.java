package com.example.application.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.BatchDispatchResult;
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
	public BatchDispatchResult dispatch(Notification notification) {
		// 이미 처리 완료(SENT/FAILED)된 알림은 재발송 없이 success 반환
		if (notification.isTerminal()) {
			return BatchDispatchResult.success(notification.getId());
		}

		Notification managed = notificationRepository.findById(notification.getId()).orElseThrow();

		// 1단계: PENDING → SENDING 상태 전환
		managed.startSending();

		// 2단계: 외부 API 호출
		SendResult sendResult = sendNotification(managed);

		// 3단계: 결과에 따라 SENT/FAILED 상태 반영
		if (sendResult.isSuccess()) {
			managed.markAsSent();
			eventPublisher.publishEvent(new AdminStatsChangedEvent());
			return BatchDispatchResult.success(managed.getId());
		} else if (sendResult.isNonRetryableFailure()) {
			managed.markAsFailed(sendResult.failReason());
			eventPublisher.publishEvent(new AdminStatsChangedEvent());
			return BatchDispatchResult.failNonRetryable(managed.getId(), sendResult.failReason());
		} else {
			return BatchDispatchResult.failRetryable(managed.getId(), sendResult.failReason(),
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
