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
		if (request.notificationId() == null) {
			return RecordProcessResult.missingNotificationId(request.contextId(), request.retryCount());
		}

		if (!lockManager.tryAcquire(request.notificationId())) {
			return RecordProcessResult.skippedForConcurrentProcessing(
				request.contextId(), request.notificationId(), request.retryCount());
		}

		DispatchResult dispatchResult;
		try {
			dispatchResult = dispatchService.dispatch(request.notificationId());
		} catch (OptimisticLockingFailureException e) {
			lockManager.release(request.notificationId());
			return RecordProcessResult.skippedForLockConflict(
				request.contextId(), request.notificationId(), request.retryCount());
		}

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
		if (dispatchResult.isNonRetryableFailure()) {
			return RecordProcessResult.nonRetryableFailure(
				request.contextId(), request.notificationId(), request.retryCount(), dispatchResult.failReason());
		}
		if (request.retryCount() >= properties.resolveMaxRetryCount()) {
			return RecordProcessResult.maxRetryExceeded(
				request.contextId(), request.notificationId(), request.retryCount(),
				properties.resolveMaxRetryCount(), dispatchResult.failReason());
		}
		return RecordProcessResult.retryableFailure(
			request.contextId(), request.notificationId(), request.retryCount(),
			dispatchResult.failReason(), dispatchResult.retryDelayMillis());
	}
}
