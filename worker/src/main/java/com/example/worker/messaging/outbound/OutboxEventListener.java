package com.example.worker.messaging.outbound;

import java.util.List;
import java.util.ArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.event.OutboxSavedEvent;
import com.example.application.port.out.repository.OutboxRepository;
import com.example.worker.config.rabbitmq.RabbitPropertyKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = RabbitPropertyKeys.MESSAGING_ENABLED, havingValue = "true")
public class OutboxEventListener {

	private final NotificationEventPublisher eventPublisher;
	private final OutboxRepository outboxRepository;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onOutboxSaved(OutboxSavedEvent event) {
		List<Long> ids = event.notificationIds();
		if (ids.isEmpty()) {
			return;
		}

		List<Long> publishedIds = new ArrayList<>(ids.size());
		boolean allPublished = true;
		for (Long notificationId : ids) {
			if (publishIfPossible(notificationId)) {
				publishedIds.add(notificationId);
				continue;
			}
			allPublished = false;
		}

		// 삭제 일괄 처리
		if (allPublished) {
			outboxRepository.deleteByAggregateId(event.groupId());
		}

		if (!publishedIds.isEmpty()) {
			log.info("즉시 발행 완료: success={}, total={}", publishedIds.size(), ids.size());
		}
	}

	private boolean publishIfPossible(Long notificationId) {
		try {
			eventPublisher.publish(notificationId);
			return true;
		} catch (Exception e) {
			log.debug("즉시 발행 실패(재시도 예정): notificationId={}, reason={}",
				notificationId, e.getMessage());
			return false;
		}
	}
}
