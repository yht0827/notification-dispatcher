package com.example.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.application.mapper.NotificationResultMapper;
import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.in.result.BatchDispatchResult;
import com.example.application.port.in.result.NotificationDispatchResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.cache.NotificationGroupListCacheRepository;
import com.example.application.port.out.cache.NotificationUnreadCountCacheRepository;
import com.example.application.port.out.repository.NotificationFailureUpdate;
import com.example.application.port.out.repository.NotificationGroupCountUpdate;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.port.out.result.SendResult;
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
	private final NotificationGroupListCacheRepository groupListCacheRepository;
	private final NotificationUnreadCountCacheRepository unreadCountCacheRepository;
	private final NotificationResultMapper mapper;

	@Override
	@Transactional
	public NotificationDispatchResult dispatch(Notification notification) {
		// 메시지 중복 처리 방지
		if (notification.isTerminal()) {
			log.debug("이미 종결 상태인 알림 발송 생략: id={}, status={}",
				notification.getId(), notification.getStatus());
			return NotificationDispatchResult.success();
		}

		// PENDING → SENDING
		notification.startSending();
		Notification managedNotification = notificationRepository.save(notification);

		// API 전송
		SendResult sendResult = notificationSender.send(managedNotification);

		if (sendResult.isSuccess()) {
			// SENDING → SENT
			managedNotification.markAsSent();
			notificationRepository.save(managedNotification);
			scheduleWriteThroughAfterCommit(managedNotification);
			log.info("알림 발송 성공: id={}, receiver={}", managedNotification.getId(), managedNotification.getReceiver());
			return NotificationDispatchResult.success();
		} else {
			log.warn("알림 발송 실패: id={}, reason={}", managedNotification.getId(), sendResult.failReason());
			evictGroupList(managedNotification);
			if (sendResult.isNonRetryableFailure()) {
				return NotificationDispatchResult.failNonRetryable(sendResult.failReason());
			}
			return NotificationDispatchResult.failRetryable(sendResult.failReason(), sendResult.retryDelayMillis());
		}
	}

	@Override
	public List<BatchDispatchResult> dispatchBatch(List<Notification> notifications) {
		if (notifications == null || notifications.isEmpty()) {
			return List.of();
		}

		List<Notification> dispatchCandidates = notifications.stream()
			.filter(notification -> !notification.isTerminal())
			.toList();

		Map<Long, Notification> preparedById = prepareBatchForDispatch(dispatchCandidates);
		Map<Long, SendResult> sendResults = sendBatch(preparedById);
		Map<Long, BatchDispatchResult> finalResultsById = persistBatchDispatchResults(preparedById, sendResults);

		List<BatchDispatchResult> finalResults = new ArrayList<>(notifications.size());
		for (Notification notification : notifications) {
			if (notification.isTerminal()) {
				finalResults.add(BatchDispatchResult.success(notification.getId()));
				continue;
			}
			finalResults.add(finalResultsById.getOrDefault(notification.getId(),
				BatchDispatchResult.failRetryable(notification.getId(), "배치 처리 결과 누락")));
		}
		return finalResults;
	}

	@Override
	@Transactional
	public void markAsFailed(Long notificationId, String reason) {
		// SENDING → FAILED
		notificationRepository.findById(notificationId).ifPresent(notification -> {
			notification.markAsFailed(reason);
			notificationRepository.save(notification);
			evictGroupList(notification);
			log.error("알림 최종 실패: id={}, reason={}", notificationId, reason);
		});
	}

	private Map<Long, Notification> prepareBatchForDispatch(List<Notification> notifications) {
		return transactionTemplate.execute(status -> {
			if (notifications.isEmpty()) {
				return Map.of();
			}

			Map<Long, Notification> preparedById = new LinkedHashMap<>();
			List<Long> notificationIds = new ArrayList<>(notifications.size());
			for (Notification notification : notifications) {
				notificationIds.add(notification.getId());
				preparedById.put(notification.getId(), notification);
			}
			notificationRepository.bulkStartSending(notificationIds, java.time.LocalDateTime.now());
			evictGroupLists(preparedById.values());
			return preparedById;
		});
	}

	private Map<Long, SendResult> sendBatch(Map<Long, Notification> preparedById) {
		Map<Long, SendResult> sendResults = new LinkedHashMap<>();
		for (Notification notification : preparedById.values()) {
			try {
				SendResult sendResult = notificationSender.send(notification);
				sendResults.put(notification.getId(), sendResult);
			} catch (UnsupportedChannelException unsupportedChannelException) {
				sendResults.put(notification.getId(),
					SendResult.failNonRetryable(unsupportedChannelException.getMessage()));
			} catch (RuntimeException exception) {
				sendResults.put(notification.getId(), SendResult.fail(exception.getMessage()));
			}
		}
		return sendResults;
	}

	private Map<Long, BatchDispatchResult> persistBatchDispatchResults(Map<Long, Notification> preparedById,
		Map<Long, SendResult> sendResults) {
		return transactionTemplate.execute(status -> {
			if (sendResults.isEmpty()) {
				return Map.of();
			}

			List<Long> sentIds = new ArrayList<>();
			List<NotificationFailureUpdate> failedUpdates = new ArrayList<>();
			Map<Long, NotificationGroupCountUpdate> groupCountUpdates = new LinkedHashMap<>();
			Map<Long, BatchDispatchResult> results = new LinkedHashMap<>();
			for (Long notificationId : sendResults.keySet()) {
				Notification notification = preparedById.get(notificationId);
				if (notification == null) {
					results.put(notificationId, BatchDispatchResult.failNonRetryable(notificationId,
						"알림을 찾을 수 없음: " + notificationId));
					continue;
				}

				SendResult sendResult = sendResults.get(notificationId);
				if (sendResult.isSuccess()) {
					sentIds.add(notificationId);
					accumulateGroupCount(groupCountUpdates, notification, 1, 0);
					results.put(notificationId, BatchDispatchResult.success(notificationId));
					// unread count evict for batch SENT
					if (unreadCountCacheRepository.enabled()) {
						String clientId = notification.getGroup() != null ? notification.getGroup().getClientId() : null;
						String receiver = notification.getReceiver();
						if (clientId != null && receiver != null) {
							unreadCountCacheRepository.evict(clientId, receiver);
						}
					}
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

			java.time.LocalDateTime updatedAt = java.time.LocalDateTime.now();
			notificationRepository.bulkMarkAsSent(sentIds, updatedAt, updatedAt);
			notificationRepository.bulkMarkAsFailed(failedUpdates, updatedAt);
			if (!groupCountUpdates.isEmpty()) {
				notificationGroupRepository.bulkApplyDispatchCounts(List.copyOf(groupCountUpdates.values()));
			}
			evictGroupLists(preparedById.values());
			return results;
		});
	}

	private void accumulateGroupCount(Map<Long, NotificationGroupCountUpdate> groupCountUpdates,
		Notification notification, int sentDelta, int failedDelta) {
		if (notification.getGroup() == null || notification.getGroup().getId() == null) {
			return;
		}

		Long groupId = notification.getGroup().getId();
		NotificationGroupCountUpdate current = groupCountUpdates.get(groupId);
		if (current == null) {
			groupCountUpdates.put(groupId, new NotificationGroupCountUpdate(groupId, sentDelta, failedDelta));
			return;
		}
		groupCountUpdates.put(groupId, new NotificationGroupCountUpdate(
			groupId,
			current.sentDelta() + sentDelta,
			current.failedDelta() + failedDelta
		));
	}

	private void scheduleWriteThroughAfterCommit(Notification notification) {
		String clientId = notification.getGroup() != null ? notification.getGroup().getClientId() : null;
		String receiver = notification.getReceiver();
		if (clientId == null) {
			return;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					writeThroughAfterSent(clientId, receiver);
				}
			});
		} else {
			writeThroughAfterSent(clientId, receiver);
		}
	}

	private void writeThroughAfterSent(Notification notification) {
		if (notification.getGroup() == null) {
			return;
		}
		writeThroughAfterSent(notification.getGroup().getClientId(), notification.getReceiver());
	}

	private void writeThroughAfterSent(String clientId, String receiver) {
		if (unreadCountCacheRepository.enabled()) {
			try {
				LocalDateTime from = LocalDateTime.now().minusDays(7);
				long count = notificationRepository.countUnreadByClientIdAndReceiver(clientId, receiver, from);
				unreadCountCacheRepository.put(clientId, receiver, count);
			} catch (Exception e) {
				log.warn("unread count cache write-through 실패: clientId={}, receiver={}", clientId, receiver, e);
			}
		}

		if (groupListCacheRepository.enabled()) {
			try {
				LocalDateTime from = LocalDateTime.now().minusDays(7);
				List<NotificationGroupResult> groups = notificationGroupRepository
					.findByClientIdWithCursor(clientId, from, null, groupListCacheRepository.latestLimit())
					.stream()
					.map(mapper::toGroupResult)
					.toList();
				groupListCacheRepository.putLatest(clientId, groups);
			} catch (Exception e) {
				log.warn("group list cache write-through 실패: clientId={}", clientId, e);
			}
		}
	}

	private void evictGroupList(Notification notification) {
		if (notification == null || notification.getGroup() == null) {
			return;
		}
		String clientId = notification.getGroup().getClientId();
		if (clientId == null || clientId.isBlank()) {
			return;
		}
		groupListCacheRepository.evictLatest(clientId);
	}

	private void evictGroupLists(Iterable<Notification> notifications) {
		Set<String> clientIds = new LinkedHashSet<>();
		for (Notification notification : notifications) {
			if (notification == null || notification.getGroup() == null) {
				continue;
			}
			String clientId = notification.getGroup().getClientId();
			if (clientId == null || clientId.isBlank()) {
				continue;
			}
			clientIds.add(clientId);
		}
		clientIds.forEach(groupListCacheRepository::evictLatest);
	}
}
