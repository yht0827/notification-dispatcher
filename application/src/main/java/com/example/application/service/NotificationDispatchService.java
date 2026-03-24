package com.example.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.BatchDispatchResult;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.SendResult;
import com.example.application.port.out.event.AdminStatsChangedEvent;
import com.example.application.port.out.repository.NotificationFailureUpdate;
import com.example.application.port.out.repository.NotificationGroupCountUpdate;
import com.example.application.port.out.repository.NotificationGroupRepository;
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
	private final NotificationGroupRepository notificationGroupRepository;
	private final NotificationSender notificationSender;
	private final TransactionTemplate transactionTemplate;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	public List<BatchDispatchResult> dispatchBatch(List<Notification> notifications) {
		if (notifications == null || notifications.isEmpty()) {
			return List.of();
		}

		List<Notification> dispatchCandidates = notifications.stream()
			.filter(n -> !n.isTerminal())
			.toList();

		Map<Long, Notification> preparedById = prepareBatchForDispatch(dispatchCandidates);
		Map<Long, SendResult> sendResults = sendBatch(preparedById);
		Map<Long, BatchDispatchResult> finalResultsById = persistBatchDispatchResults(preparedById, sendResults);

		return notifications.stream()
			.map(n -> n.isTerminal()
				? BatchDispatchResult.success(n.getId())
				: finalResultsById.getOrDefault(n.getId(),
				BatchDispatchResult.failRetryable(n.getId(), "배치 처리 결과 누락")))
			.toList();
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

	private Map<Long, Notification> prepareBatchForDispatch(List<Notification> notifications) {
		return transactionTemplate.execute(status -> {
			if (notifications.isEmpty()) {
				return Map.of();
			}

			Map<Long, Notification> preparedById = notifications.stream()
				.collect(LinkedHashMap::new, (map, n) -> map.put(n.getId(), n), Map::putAll);

			notificationRepository.bulkStartSending(List.copyOf(preparedById.keySet()), LocalDateTime.now());
			eventPublisher.publishEvent(new AdminStatsChangedEvent());
			return preparedById;
		});
	}

	private Map<Long, SendResult> sendBatch(Map<Long, Notification> preparedById) {
		if (preparedById.isEmpty()) {
			return Map.of();
		}

		List<Notification> notifications = List.copyOf(preparedById.values());
		Map<Long, SendResult> batchResults = notificationSender.sendBatch(notifications);
		if (batchResults != null && batchResults.size() == notifications.size()) {
			return batchResults;
		}

		return sendBatchSequential(preparedById);
	}

	private Map<Long, SendResult> sendBatchSequential(Map<Long, Notification> preparedById) {
		Map<Long, SendResult> sendResults = new LinkedHashMap<>();
		for (Notification notification : preparedById.values()) {
			sendResults.put(notification.getId(), sendNotification(notification));
		}
		return sendResults;
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

	private Map<Long, BatchDispatchResult> persistBatchDispatchResults(
		Map<Long, Notification> preparedById, Map<Long, SendResult> sendResults) {
		return transactionTemplate.execute(status -> {
			if (sendResults.isEmpty()) {
				return Map.of();
			}

			List<Long> sentIds = new ArrayList<>();
			List<NotificationFailureUpdate> failedUpdates = new ArrayList<>();
			Map<Long, NotificationGroupCountUpdate> groupCountUpdates = new LinkedHashMap<>();
			Map<Long, BatchDispatchResult> results = new LinkedHashMap<>();

			for (Map.Entry<Long, SendResult> entry : sendResults.entrySet()) {
				Long notificationId = entry.getKey();
				SendResult sendResult = entry.getValue();
				Notification notification = preparedById.get(notificationId);

				if (notification == null) {
					results.put(notificationId,
						BatchDispatchResult.failNonRetryable(notificationId, "알림을 찾을 수 없음: " + notificationId));
					continue;
				}

				if (sendResult.isSuccess()) {
					sentIds.add(notificationId);
					accumulateGroupCount(groupCountUpdates, notification, 1, 0);
					results.put(notificationId, BatchDispatchResult.success(notificationId));
				} else if (sendResult.isNonRetryableFailure()) {
					failedUpdates.add(new NotificationFailureUpdate(notificationId, sendResult.failReason()));
					accumulateGroupCount(groupCountUpdates, notification, 0, 1);
					results.put(notificationId,
						BatchDispatchResult.failNonRetryable(notificationId, sendResult.failReason()));
				} else {
					results.put(notificationId,
						BatchDispatchResult.failRetryable(notificationId, sendResult.failReason(),
							sendResult.retryDelayMillis()));
				}
			}

			LocalDateTime now = LocalDateTime.now();
			notificationRepository.bulkMarkAsSent(sentIds, now, now);
			notificationRepository.bulkMarkAsFailed(failedUpdates, now);
			if (!groupCountUpdates.isEmpty()) {
				notificationGroupRepository.bulkApplyDispatchCounts(List.copyOf(groupCountUpdates.values()));
			}
			eventPublisher.publishEvent(new AdminStatsChangedEvent());
			return results;
		});
	}

	private void accumulateGroupCount(Map<Long, NotificationGroupCountUpdate> groupCountUpdates,
		Notification notification, int sentDelta, int failedDelta) {
		if (notification.getGroup() == null || notification.getGroup().getId() == null) {
			return;
		}
		Long groupId = notification.getGroup().getId();
		groupCountUpdates.compute(groupId, (id, current) -> current == null
			? new NotificationGroupCountUpdate(id, sentDelta, failedDelta)
			:
			new NotificationGroupCountUpdate(id, current.sentDelta() + sentDelta, current.failedDelta() + failedDelta));
	}
}
