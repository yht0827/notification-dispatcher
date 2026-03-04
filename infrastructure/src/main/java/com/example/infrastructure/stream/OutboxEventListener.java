package com.example.infrastructure.stream;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.OutboxRepository;
import com.example.application.service.event.OutboxSavedEvent;
import com.example.infrastructure.config.rabbitmq.NotificationRabbitConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = NotificationRabbitConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
public class OutboxEventListener {

	private final NotificationEventPublisher eventPublisher;
	private final OutboxRepository outboxRepository;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onOutboxSaved(OutboxSavedEvent event) {
		List<Long> ids = event.notificationIds();

		long success = ids.stream()
			.filter(this::publishAndDeleteIfPossible)
			.count();

		if (success > 0) {
			log.info("즉시 발행 완료: success={}, total={}", success, ids.size());
		}
	}

	private boolean publishAndDeleteIfPossible(Long notificationId) {
		try {
			eventPublisher.publish(notificationId);
			outboxRepository.deleteByAggregateId(notificationId);
			return true;
		} catch (Exception e) {
			log.debug("즉시 발행 실패(재시도 예정): notificationId={}, reason={}",
				notificationId, e.getMessage());
			return false;
		}
	}
}
