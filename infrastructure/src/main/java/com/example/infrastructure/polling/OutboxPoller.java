package com.example.infrastructure.polling;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.repository.OutboxRepository;
import com.example.domain.outbox.Outbox;
import com.example.domain.outbox.OutboxAggregateType;
import com.example.domain.outbox.OutboxStatus;
import com.example.infrastructure.config.rabbitmq.RabbitPropertyKeys;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = RabbitPropertyKeys.MESSAGING_ENABLED, havingValue = "true")
@ConditionalOnProperty(name = "app.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

	private final OutboxRepository outboxRepository;
	private final NotificationEventPublisher eventPublisher;
	private final OutboxProperties outboxProperties;
	private final MeterRegistry meterRegistry;

	private static final int MAX_RETRY = 3;

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
			// 2. 발행 시도
			if (publishEvent(outbox)) {
				outbox.markAsProcessed();
				processed.add(outbox);
			} else {
				outbox.incrementRetry();
				if (outbox.isExhausted(MAX_RETRY)) {
					outbox.markAsFailed();
					meterRegistry.counter("notification.outbox.failed").increment();
					log.error("Outbox 최대 재시도 초과 - FAILED 처리: outboxId={}, aggregateId={}", outbox.getId(), outbox.getAggregateId());
				}
			}
		}

		// 3. 성공한 것만 삭제
		if (!processed.isEmpty()) {
			outboxRepository.deleteAll(processed);
			meterRegistry.counter("notification.outbox.published").increment(processed.size());
			log.info("Outbox 처리 완료: count={}", processed.size());
		}
	}

	private boolean publishEvent(Outbox outbox) {
		try {
			if (outbox.getAggregateType() == OutboxAggregateType.GROUP) {
				for (Long notificationId : parseNotificationIds(outbox.getPayload())) {
					eventPublisher.publish(notificationId);
				}
			} else {
				eventPublisher.publish(outbox.getAggregateId());
			}
			return true;
		} catch (Exception e) {
			meterRegistry.counter("notification.outbox.publish_failed").increment();
			log.error("메시징 발행 실패: outboxId={}, aggregateId={}, reason={}",
				outbox.getId(), outbox.getAggregateId(), e.getMessage());
			return false;
		}
	}

	private List<Long> parseNotificationIds(String payload) {
		if (payload == null || payload.isBlank()) {
			return List.of();
		}

		return Arrays.stream(payload.split(","))
			.map(String::trim)
			.filter(value -> !value.isEmpty())
			.map(Long::parseLong)
			.toList();
	}
}
