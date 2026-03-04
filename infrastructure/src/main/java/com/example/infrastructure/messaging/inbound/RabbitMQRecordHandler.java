package com.example.infrastructure.messaging.inbound;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.NotificationDispatchResult;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.exception.InvalidStatusTransitionException;
import com.example.domain.exception.UnsupportedChannelException;
import com.example.domain.notification.Notification;
import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.exception.NonRetryableMessageException;
import com.example.infrastructure.messaging.exception.RetryableMessageException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQRecordHandler {

	private final NotificationRepository notificationRepository;
	private final NotificationDispatchUseCase dispatchService;
	private final NotificationRabbitProperties properties;
	private final DispatchLockManager lockManager;

	public void process(Long notificationId, int retryCount) {
		validateNotificationId(notificationId);

		if (!lockManager.tryAcquire(notificationId)) {
			log.info("이미 처리 중인 알림 스킵: notificationId={}", notificationId);
			return;
		}

		try {
			Notification notification = loadNotification(notificationId);
			NotificationDispatchResult dispatchResult = dispatchService.dispatch(notification);
			if (dispatchResult.isFailure()) {
				throw toDispatchFailureException(notificationId, retryCount, dispatchResult.failReason());
			}
			lockManager.release(notificationId);
		} catch (RuntimeException e) {
			throw handleException(notificationId, e);
		}
	}

	private void validateNotificationId(Long notificationId) {
		if (notificationId == null) {
			throw new NonRetryableMessageException("notificationId 값이 비어 있습니다.");
		}
	}

	private Notification loadNotification(Long notificationId) {
		return notificationRepository.findById(notificationId)
			.orElseThrow(() -> new NonRetryableMessageException("알림을 찾을 수 없음: " + notificationId));
	}

	private RuntimeException handleException(Long notificationId, RuntimeException exception) {
		RuntimeException messageException = mapToMessageException(notificationId, exception);
		if (!(messageException instanceof NonRetryableMessageException)) {
			lockManager.release(notificationId);
		}
		return messageException;
	}

	private RuntimeException mapToMessageException(Long notificationId, RuntimeException exception) {
		if (exception instanceof NonRetryableMessageException
			|| exception instanceof RetryableMessageException) {
			return exception;
		}

		if (exception instanceof ObjectOptimisticLockingFailureException) {
			log.info("낙관적 락 충돌 (이미 처리됨): notificationId={}", notificationId);
			return new NonRetryableMessageException("낙관적 락 충돌: 이미 다른 인스턴스에서 처리됨", exception);
		}

		if (exception instanceof InvalidStatusTransitionException e) {
			return toNonRetryableAfterMarkFailed(
				notificationId,
				"상태 전이 오류",
				"알림 상태 전이 오류로 재시도하지 않습니다.",
				e
			);
		}

		if (exception instanceof UnsupportedChannelException unsupportedChannel) {
			String reason = unsupportedChannel.getMessage();
			return toNonRetryableAfterMarkFailed(
				notificationId,
				reason,
				"지원하지 않는 채널로 재시도하지 않습니다: " + reason,
				unsupportedChannel
			);
		}

		log.error("예상치 못한 예외 발생: notificationId={}", notificationId, exception);
		return new RetryableMessageException("예상치 못한 오류: " + exception.getMessage(), exception);
	}

	private RuntimeException toDispatchFailureException(Long notificationId, int retryCount, String reason) {
		String failureReason = normalizeReason(reason);
		if (retryCount >= properties.resolveMaxRetryCount()) {
			return toNonRetryableAfterMarkFailed(notificationId, failureReason, "재시도 한도 초과: " + failureReason, null);
		}
		return new RetryableMessageException("알림 발송 실패: " + failureReason);
	}

	private NonRetryableMessageException toNonRetryableAfterMarkFailed(Long notificationId, String reason,
		String message, Exception cause) {
		dispatchService.markAsFailed(notificationId, reason);
		if (cause == null) {
			return new NonRetryableMessageException(message);
		}
		return new NonRetryableMessageException(message, cause);
	}

	private String normalizeReason(String reason) {
		if (reason == null || reason.isBlank()) {
			return "알 수 없는 오류";
		}
		return reason;
	}
}
