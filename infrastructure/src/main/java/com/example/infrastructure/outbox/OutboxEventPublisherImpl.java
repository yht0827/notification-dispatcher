package com.example.infrastructure.outbox;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.application.port.out.OutboxEventPublisher;
import com.example.infrastructure.config.NotificationConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = NotificationConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
public class OutboxEventPublisherImpl implements OutboxEventPublisher {

	private final ApplicationEventPublisher eventPublisher;

	@Override
	public void publishAfterCommit(List<Long> notificationIds) {
		if (notificationIds.isEmpty()) {
			return;
		}
		eventPublisher.publishEvent(new OutboxCreatedEvent(notificationIds));
	}
}
