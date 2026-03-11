package com.example.infrastructure.messaging.inbound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
			.collect(LinkedHashMap::new, (map, result)
				-> map.put(result.notificationId(), result), Map::putAll);

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

	private Map<Long, Notification> loadNotifications(List<Long> notificationIds) {
		return notificationRepository.findAllByIdIn(notificationIds).stream()
			.collect(LinkedHashMap::new, (map, notification) -> map.put(notification.getId(), notification),
				Map::putAll);
	}

	private boolean shouldReleaseLock(RecordProcessResult result) {
		return result.isSuccess() || result.isRetryableFailure();
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
