package com.example.infrastructure.stream.inbound;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.NotificationDispatchUseCase.DispatchResult;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.NotificationRepository;
import com.example.domain.exception.InvalidStatusTransitionException;
import com.example.domain.exception.UnsupportedChannelException;
import com.example.domain.notification.Notification;
import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.stream.exception.NonRetryableStreamMessageException;
import com.example.infrastructure.stream.exception.RetryableStreamMessageException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RedisStreamRecordHandler {

	private final NotificationRepository notificationRepository;
	private final NotificationDispatchUseCase dispatchService;
	private final NotificationStreamProperties properties;
	private final DispatchLockManager lockManager;

	public void process(Long notificationId, int retryCount) {
		runWithDispatchLock(notificationId, () -> processInternal(notificationId, retryCount));
	}

	private void runWithDispatchLock(Long notificationId, Runnable action) {
		// 락 획득
		if (!lockManager.tryAcquire(notificationId)) {
			log.info("이미 처리 중인 알림 스킵: notificationId={}", notificationId);
			return;
		}

		try {
			action.run();
		} catch (Exception e) {
			throw translateExceptionWithLockPolicy(notificationId, e);
		}
	}

	private void processInternal(Long notificationId, int retryCount) {
		Notification notification = loadNotification(notificationId);
		DispatchResult dispatchResult = dispatchSafely(notificationId, notification);
		throwRetryExceptionIfDispatchFailed(notificationId, retryCount, dispatchResult);
	}

	private RuntimeException translateExceptionWithLockPolicy(Long notificationId, Exception e) {
		// non-retryable은 DLQ 경로로 넘기고 락을 유지(중복 소비 방지)
		if (e instanceof NonRetryableStreamMessageException nonRetryable) {
			return nonRetryable;
		}

		// retryable/예상치 못한 예외는 즉시 락 해제 후 재처리 가능하게 함
		lockManager.release(notificationId);
		if (e instanceof RetryableStreamMessageException retryable) {
			return retryable;
		}

		log.error("예상치 못한 예외 발생: notificationId={}", notificationId, e);
		return new RetryableStreamMessageException("예상치 못한 오류: " + e.getMessage(), e);
	}

	private void throwRetryExceptionIfDispatchFailed(Long notificationId, int retryCount,
		DispatchResult dispatchResult) {
		if (dispatchResult.isFailure()) {
			handleSendFailure(notificationId, retryCount, dispatchResult.failReason());
		}
	}

	private Notification loadNotification(Long notificationId) {
		if (notificationId == null) {
			throw new NonRetryableStreamMessageException("notificationId 값이 비어 있습니다.");
		}

		return notificationRepository.findById(notificationId)
			.orElseThrow(() -> new NonRetryableStreamMessageException("알림을 찾을 수 없음: " + notificationId));
	}

	private DispatchResult dispatchSafely(Long notificationId, Notification notification) {
		try {
			return dispatchService.dispatch(notification);
		} catch (InvalidStatusTransitionException e) {
			throw toNonRetryableAfterMarkFailed(
				notificationId,
				"상태 전이 오류",
				"알림 상태 전이 오류로 재시도하지 않습니다.",
				e
			);
		} catch (UnsupportedChannelException e) {
			throw toNonRetryableAfterMarkFailed(
				notificationId,
				e.getMessage(),
				"지원하지 않는 채널로 재시도하지 않습니다: " + e.getMessage(),
				e
			);
		}
	}

	private void handleSendFailure(Long notificationId, int retryCount, String reason) {
		// 재시도 한도 초과 시 DB 실패 상태를 남기고 non-retryable로 전환
		if (retryCount >= properties.resolveMaxRetryCount()) {
			throw toNonRetryableAfterMarkFailed(notificationId, reason, "재시도 한도 초과: " + reason, null);
		}
		// 재시도 가능 실패는 WAIT 스트림 경로로 전달
		throw new RetryableStreamMessageException("알림 발송 실패: " + reason);
	}

	private NonRetryableStreamMessageException toNonRetryableAfterMarkFailed(Long notificationId, String reason,
		String message, Exception cause) {
		dispatchService.markAsFailed(notificationId, reason);
		if (cause == null) {
			return new NonRetryableStreamMessageException(message);
		}
		return new NonRetryableStreamMessageException(message, cause);
	}
}
