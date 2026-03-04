package com.example.infrastructure.polling;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.OutboxRepository;
import com.example.domain.outbox.Outbox;
import com.example.domain.outbox.OutboxStatus;
import com.example.infrastructure.config.rabbitmq.NotificationRabbitConfig;
import com.example.infrastructure.polling.OutboxProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = NotificationRabbitConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
public class OutboxPoller {

	private final OutboxRepository outboxRepository;
	private final NotificationEventPublisher streamPublisher;
	private final OutboxProperties outboxProperties;

	@Scheduled(fixedDelayString = "${outbox.poll-interval-millis:1000}")
	@Transactional
	public void pollAndPublish() {
		// 1. PENDING 상태 Outbox 조회
		List<Outbox> pendingOutboxes = outboxRepository.findByStatus(
			OutboxStatus.PENDING,
			outboxProperties.resolveBatchSize()
		);
		if (pendingOutboxes.isEmpty()) {
			return;
		}

		List<Outbox> processed = new ArrayList<>();
		for (Outbox outbox : pendingOutboxes) {
			// 2. Redis 발행 시도
			if (publishToStream(outbox)) {
				outbox.markAsProcessed();
				processed.add(outbox); // 성공한 것만 리스트에 추가
			}
		}

		// 3. 성공한 것만 삭제
		if (!processed.isEmpty()) {
			outboxRepository.deleteAll(processed);
			log.info("Outbox 처리 완료: count={}", processed.size());
		}
	}

	private boolean publishToStream(Outbox outbox) {
		try {
			streamPublisher.publish(outbox.getAggregateId());
			return true;
		} catch (Exception e) {
			log.error("Redis Stream 발행 실패: outboxId={}, aggregateId={}, reason={}",
				outbox.getId(), outbox.getAggregateId(), e.getMessage());
			return false;
		}
	}
}
