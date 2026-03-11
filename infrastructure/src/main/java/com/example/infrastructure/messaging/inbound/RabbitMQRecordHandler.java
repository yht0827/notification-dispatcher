package com.example.infrastructure.messaging.inbound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.BatchDispatchResult;
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

	private static final String UNKNOWN_ERROR_REASON = "알 수 없는 오류";
	private static final String STATUS_TRANSITION_REASON_PREFIX = "상태 전이 오류";

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
			List<BatchDispatchResult> results = dispatchService.dispatchBatch(List.of(notification));
			BatchDispatchResult dispatchResult = results.isEmpty() ? null : results.get(0);
			if (dispatchResult == null || dispatchResult.isFailure()) {
				throw toDispatchFailureException(notificationId, retryCount, dispatchResult);
			}
			lockManager.release(notificationId);
		} catch (RuntimeException e) {
			throw handleException(notificationId, e);
		}
	}

	public List<RecordProcessResult> processBatch(List<RecordProcessRequest> requests) {
		if (requests == null || requests.isEmpty()) {
			return List.of();
		}

		Map<Long, RecordProcessResult> resultsByContextId = new LinkedHashMap<>();
		Map<Long, RecordProcessRequest> firstRequestByNotificationId = new LinkedHashMap<>();
		for (RecordProcessRequest request : requests) {
			if (request.notificationId() == null) {
				resultsByContextId.put(request.contextId(),
					RecordProcessResult.nonRetryableFailure(
						request.contextId(),
						null,
						request.retryCount(),
						"notificationId 값이 비어 있습니다."
					));
				continue;
			}
			if (firstRequestByNotificationId.containsKey(request.notificationId())) {
				resultsByContextId.put(request.contextId(),
					RecordProcessResult.skipped(
						request.contextId(),
						request.notificationId(),
						request.retryCount(),
						"동일 배치 내 중복 notificationId 스킵"
					));
				continue;
			}
			firstRequestByNotificationId.put(request.notificationId(), request);
		}

		Map<Long, RecordProcessRequest> acquiredRequests = new LinkedHashMap<>();
		for (RecordProcessRequest request : firstRequestByNotificationId.values()) {
			if (!lockManager.tryAcquire(request.notificationId())) {
				resultsByContextId.put(request.contextId(),
					RecordProcessResult.skipped(
						request.contextId(),
						request.notificationId(),
						request.retryCount(),
						"이미 처리 중인 알림 스킵"
					));
				continue;
			}
			acquiredRequests.put(request.notificationId(), request);
		}

		if (acquiredRequests.isEmpty()) {
			return toOrderedResults(requests, resultsByContextId);
		}

		Map<Long, Notification> notificationsById = loadNotifications(acquiredRequests.keySet().stream().toList());
		List<Notification> dispatchCandidates = new ArrayList<>();
		for (RecordProcessRequest request : acquiredRequests.values()) {
			Notification notification = notificationsById.get(request.notificationId());
			if (notification == null) {
				resultsByContextId.put(request.contextId(),
					RecordProcessResult.nonRetryableFailure(
						request.contextId(),
						request.notificationId(),
						request.retryCount(),
						"알림을 찾을 수 없음: " + request.notificationId()
					));
				continue;
			}
			dispatchCandidates.add(notification);
		}

		Map<Long, BatchDispatchResult> dispatchResultsById = dispatchService.dispatchBatch(dispatchCandidates).stream()
			.collect(LinkedHashMap::new, (map, result) -> map.put(result.notificationId(), result), Map::putAll);

		for (RecordProcessRequest request : acquiredRequests.values()) {
			if (resultsByContextId.containsKey(request.contextId())) {
				continue;
			}

			BatchDispatchResult dispatchResult = dispatchResultsById.get(request.notificationId());
			if (dispatchResult == null) {
				resultsByContextId.put(request.contextId(),
					RecordProcessResult.retryableFailure(
						request.contextId(),
						request.notificationId(),
						request.retryCount(),
						"배치 처리 결과 누락"
					));
				lockManager.release(request.notificationId());
				continue;
			}

			RecordProcessResult processResult = toProcessResult(request, dispatchResult);
			resultsByContextId.put(request.contextId(), processResult);
			if (shouldReleaseLock(processResult)) {
				lockManager.release(request.notificationId());
			}
		}

		return toOrderedResults(requests, resultsByContextId);
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

	private Map<Long, Notification> loadNotifications(List<Long> notificationIds) {
		return notificationRepository.findAllByIdIn(notificationIds).stream()
			.collect(LinkedHashMap::new, (map, notification) -> map.put(notification.getId(), notification), Map::putAll);
	}

	private RuntimeException handleException(Long notificationId, RuntimeException exception) {
		RuntimeException mappedException = mapToMessageException(notificationId, exception);
		if (shouldReleaseLock(mappedException)) {
			lockManager.release(notificationId);
		}
		return mappedException;
	}

	private boolean shouldReleaseLock(RuntimeException exception) {
		return !(exception instanceof NonRetryableMessageException);
	}

	private boolean shouldReleaseLock(RecordProcessResult result) {
		return result.isSuccess() || result.isRetryableFailure();
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
			String reason = formatStatusTransitionReason(e);
			return toNonRetryableAfterMarkFailed(
				notificationId,
				reason,
				"알림 상태 전이 오류로 재시도하지 않습니다: " + reason,
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

	private RuntimeException toDispatchFailureException(Long notificationId, int retryCount,
		BatchDispatchResult dispatchResult) {
		String failureReason = normalizeReason(dispatchResult != null ? dispatchResult.failReason() : null);
		if (dispatchResult == null || dispatchResult.isNonRetryableFailure()) {
			return toNonRetryableAfterMarkFailed(
				notificationId,
				failureReason,
				"재시도 불가 발송 실패: " + failureReason,
				null);
		}

		if (retryCount >= properties.resolveMaxRetryCount()) {
			// 재시도 한도 초과 → NonRetryable로 변환 → DLQ
			return toNonRetryableAfterMarkFailed(
				notificationId,
				failureReason,
				"재시도 한도 초과: " + failureReason,
				null);
		}
		// Wait 큐로 이동
		return new RetryableMessageException("알림 발송 실패: " + failureReason, dispatchResult.retryDelayMillis());
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
		if (reason == null) {
			return UNKNOWN_ERROR_REASON;
		}

		String normalizedReason = reason.replaceAll("\\s+", " ").trim();
		if (normalizedReason.isBlank()) {
			return UNKNOWN_ERROR_REASON;
		}

		return normalizedReason;
	}

	private String formatStatusTransitionReason(InvalidStatusTransitionException exception) {
		String detail = normalizeReason(exception.getMessage());
		if (UNKNOWN_ERROR_REASON.equals(detail)) {
			return STATUS_TRANSITION_REASON_PREFIX;
		}
		return STATUS_TRANSITION_REASON_PREFIX + " - " + detail;
	}

	private RecordProcessResult toProcessResult(RecordProcessRequest request, BatchDispatchResult dispatchResult) {
		if (dispatchResult.isSuccess()) {
			return RecordProcessResult.success(request.contextId(), request.notificationId(), request.retryCount());
		}
		if (dispatchResult.isNonRetryableFailure()) {
			return RecordProcessResult.nonRetryableFailure(
				request.contextId(),
				request.notificationId(),
				request.retryCount(),
				normalizeReason(dispatchResult.failReason())
			);
		}
		return RecordProcessResult.retryableFailure(
			request.contextId(),
			request.notificationId(),
			request.retryCount(),
			normalizeReason(dispatchResult.failReason()),
			dispatchResult.retryDelayMillis()
		);
	}

	private List<RecordProcessResult> toOrderedResults(List<RecordProcessRequest> requests,
		Map<Long, RecordProcessResult> resultsByContextId) {
		List<RecordProcessResult> orderedResults = new ArrayList<>(requests.size());
		for (RecordProcessRequest request : requests) {
			orderedResults.add(resultsByContextId.getOrDefault(
				request.contextId(),
				RecordProcessResult.retryableFailure(
					request.contextId(),
					request.notificationId(),
					request.retryCount(),
					"처리 결과 누락"
				)
			));
		}
		return orderedResults;
	}
}
