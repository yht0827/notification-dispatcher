package com.example.infrastructure.messaging.sync;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.application.port.out.event.SyncDispatchEvent;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.application.service.NotificationDispatchService;
import com.example.domain.notification.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
	name = "notification.messaging.sync-listener.enabled",
	havingValue = "true",
	matchIfMissing = false
)
public class SyncDispatchEventListener {

	private final NotificationRepository notificationRepository;
	private final NotificationDispatchService notificationDispatchService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSyncDispatch(SyncDispatchEvent event) {
		List<Long> notificationIds = event.notificationIds();
		if (notificationIds.isEmpty()) {
			return;
		}

		log.info("동기 발송 시작: count={}", notificationIds.size());
		List<Notification> notifications = notificationRepository.findAllByIdIn(notificationIds);
		notificationDispatchService.dispatchBatch(notifications);
	}
}
