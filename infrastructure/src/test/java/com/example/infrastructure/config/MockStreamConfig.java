package com.example.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.OutboxEventPublisher;

@Configuration
@ConditionalOnProperty(name = NotificationStreamConfig.STREAM_ENABLED_PROPERTY, havingValue = "false")
public class MockStreamConfig {

	@Bean
	public NotificationEventPublisher notificationEventPublisher() {
		return notificationId -> {
		};
	}

	@Bean
	public OutboxEventPublisher outboxEventPublisher() {
		return notificationIds -> {
		};
	}
}
