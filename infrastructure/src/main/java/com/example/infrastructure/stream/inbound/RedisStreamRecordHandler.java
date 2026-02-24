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
		// 1. 락 획득 시도
		if (!lockManager.tryAcquire(notificationId)) {
			log.info("이미 처리 중인 알림 스킵: notificationId={}", notificationId);
			return;
		}

		boolean shouldReleaseLock = false;
		try {
			// 2. 실제 처리
			Notification notification = loadNotification(notificationId);
			DispatchResult dispatchResult = dispatchSafely(notificationId, notification);
			if (dispatchResult.isFailure()) {
				handleSendFailure(notificationId, retryCount, dispatchResult.failReason());
			}
			// 성공 시 락 유지 (TTL로 자동 만료)
		} catch (RetryableStreamMessageException e) {
			// 재시도 가능 → 락 해제
			shouldReleaseLock = true;
			throw e;
		} catch (NonRetryableStreamMessageException e) {
			// 재시도 불가 → 락 유지 (중복 처리 방지)
			throw e;
		} catch (Exception e) {
			// 예상치 못한 예외 → 락 해제 (재시도 가능하도록)
			shouldReleaseLock = true;
			log.error("예상치 못한 예외 발생: notificationId={}", notificationId, e);
			throw new RetryableStreamMessageException("예상치 못한 오류: " + e.getMessage(), e);
		} finally {
			if (shouldReleaseLock) {
				lockManager.release(notificationId);
			}
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
		if (retryCount >= properties.resolveMaxRetryCount()) {
			throw toNonRetryableAfterMarkFailed(notificationId, reason, "재시도 한도 초과: " + reason, null);
		}
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
