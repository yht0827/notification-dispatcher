package com.example.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.infrastructure.config.rabbitmq.NotificationRabbitConfig;

@Configuration
@ConditionalOnProperty(name = NotificationRabbitConfig.MESSAGING_ENABLED_PROPERTY, havingValue = "false")
public class MockMessagingConfig {

	@Bean
	public NotificationEventPublisher notificationEventPublisher() {
		return notificationId -> {
		};
	}
}
