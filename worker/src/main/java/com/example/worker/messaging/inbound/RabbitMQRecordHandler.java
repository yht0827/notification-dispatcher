package com.example.worker.messaging.inbound;

import org.springframework.dao.OptimisticLockingFailureException;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.DispatchResult;
import com.example.application.port.out.DispatchLockManager;
import com.example.worker.config.rabbitmq.NotificationRabbitProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQRecordHandler {

	private final NotificationDispatchUseCase dispatchService;
	private final DispatchLockManager lockManager;
	private final NotificationRabbitProperties properties;

	public RecordProcessResult process(RecordProcessRequest request) {
		// 필수 값 검증
		if (request.notificationId() == null) {
			return RecordProcessResult.missingNotificationId(request.contextId(), request.retryCount());
		}

		// 중복 처리 방지 — 동일 알림이 이미 처리 중이면 스킵
		if (!lockManager.tryAcquire(request.notificationId())) {
			return RecordProcessResult.skippedForConcurrentProcessing(
				request.contextId(), request.notificationId(), request.retryCount());
		}

		// 발송 시도
		DispatchResult dispatchResult;
		try {
			dispatchResult = dispatchService.dispatch(request.notificationId());
		} catch (OptimisticLockingFailureException e) {
			// 다른 인스턴스가 먼저 처리 완료 — 락 해제 후 스킵
			lockManager.release(request.notificationId());
			return RecordProcessResult.skippedForLockConflict(
				request.contextId(), request.notificationId(), request.retryCount());
		}

		// 발송 결과에 따라 후처리
		RecordProcessResult result = toProcessResult(request, dispatchResult);
		if (result.isNonRetryableFailure()) {
			dispatchService.markAsFailed(request.notificationId(), result.reason());
		}
		if (result.isSuccess() || result.isRetryableFailure()) {
			lockManager.release(request.notificationId());
		}
		return result;
	}

	private RecordProcessResult toProcessResult(RecordProcessRequest request, DispatchResult dispatchResult) {
		if (dispatchResult.isSuccess()) {
			return RecordProcessResult.success(request.contextId(), request.notificationId(), request.retryCount());
		}
		// 재시도 불가 실패 — 즉시 DLQ
		if (dispatchResult.isNonRetryableFailure()) {
			return RecordProcessResult.nonRetryableFailure(
				request.contextId(), request.notificationId(), request.retryCount(), dispatchResult.failReason());
		}
		// 재시도 한도 초과 — retryable이지만 더 이상 시도 불가
		if (request.retryCount() >= properties.resolveMaxRetryCount()) {
			return RecordProcessResult.maxRetryExceeded(
				request.contextId(), request.notificationId(), request.retryCount(),
				properties.resolveMaxRetryCount(), dispatchResult.failReason());
		}
		// 재시도 가능 — Wait 큐로 전송
		return RecordProcessResult.retryableFailure(
			request.contextId(), request.notificationId(), request.retryCount(),
			dispatchResult.failReason(), dispatchResult.retryDelayMillis());
	}
}
