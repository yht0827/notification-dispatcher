package com.example.infrastructure.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.application.port.out.OutboxRepository;
import com.example.infrastructure.config.NotificationConfig;
import com.example.infrastructure.stream.outbound.RedisStreamPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = NotificationConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
public class OutboxEventListener {

	private final RedisStreamPublisher streamPublisher;
	private final OutboxRepository outboxRepository;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onOutboxCreated(OutboxCreatedEvent event) {
		int successCount = 0;
		for (Long notificationId : event.notificationIds()) {
			if (tryPublishAndDelete(notificationId)) {
				successCount++;
			}
		}
		if (successCount > 0) {
			log.info("즉시 발행 완료: success={}, total={}", successCount, event.notificationIds().size());
		}
	}

	private boolean tryPublishAndDelete(Long notificationId) {
		try {
			streamPublisher.publish(notificationId);
			outboxRepository.deleteByAggregateId(notificationId);
			return true;
		} catch (Exception e) {
			log.debug("즉시 발행 실패, Poller가 재시도 예정: notificationId={}, reason={}",
				notificationId, e.getMessage());
			return false;
		}
	}
}
