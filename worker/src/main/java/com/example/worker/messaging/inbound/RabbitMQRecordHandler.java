package com.example.worker.messaging.inbound;

import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.BatchDispatchResult;
import com.example.application.port.out.DispatchLockManager;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.notification.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQRecordHandler {

	private static final String UNKNOWN_ERROR_REASON = "알 수 없는 오류";

	private final NotificationRepository notificationRepository;
	private final NotificationDispatchUseCase dispatchService;
	private final DispatchLockManager lockManager;

	public RecordProcessResult process(RecordProcessRequest request) {
		if (request.notificationId() == null) {
			return RecordProcessResult.nonRetryableFailure(
				request.contextId(), null, request.retryCount(), "notificationId 값이 비어 있습니다.");
		}

		if (!lockManager.tryAcquire(request.notificationId())) {
			return RecordProcessResult.skipped(
				request.contextId(), request.notificationId(), request.retryCount(), "이미 처리 중인 알림 스킵");
		}

		Notification notification = notificationRepository.findById(request.notificationId()).orElse(null);
		if (notification == null) {
			lockManager.release(request.notificationId());
			return RecordProcessResult.nonRetryableFailure(
				request.contextId(), request.notificationId(), request.retryCount(),
				"알림을 찾을 수 없음: " + request.notificationId());
		}

		List<BatchDispatchResult> dispatchResults;
		try {
			dispatchResults = dispatchService.dispatchBatch(List.of(notification));
		} catch (OptimisticLockingFailureException e) {
			lockManager.release(request.notificationId());
			return RecordProcessResult.skipped(
				request.contextId(), request.notificationId(), request.retryCount(), "낙관적 락 충돌 - 다른 인스턴스가 처리 완료");
		}
		BatchDispatchResult dispatchResult = dispatchResults.isEmpty() ? null : dispatchResults.getFirst();

		if (dispatchResult == null) {
			lockManager.release(request.notificationId());
			return RecordProcessResult.retryableFailure(
				request.contextId(), request.notificationId(), request.retryCount(), "처리 결과 누락");
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

	private RecordProcessResult toProcessResult(RecordProcessRequest request, BatchDispatchResult dispatchResult) {
		if (dispatchResult.isSuccess()) {
			return RecordProcessResult.success(request.contextId(), request.notificationId(), request.retryCount());
		}
		if (dispatchResult.isNonRetryableFailure()) {
			return RecordProcessResult.nonRetryableFailure(
				request.contextId(), request.notificationId(), request.retryCount(),
				normalizeReason(dispatchResult.failReason()));
		}
		return RecordProcessResult.retryableFailure(
			request.contextId(), request.notificationId(), request.retryCount(),
			normalizeReason(dispatchResult.failReason()), dispatchResult.retryDelayMillis());
	}

	private String normalizeReason(String reason) {
		if (reason == null || reason.isBlank()) {
			return UNKNOWN_ERROR_REASON;
		}
		return reason.replaceAll("\\s+", " ").trim();
	}
}
