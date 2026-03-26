package com.example.worker.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.worker.config.rabbitmq.RabbitPropertyKeys;

@Configuration
@ConditionalOnProperty(name = RabbitPropertyKeys.MESSAGING_ENABLED, havingValue = "false")
public class MockMessagingConfig {

	@Bean
	public NotificationEventPublisher notificationEventPublisher() {
		return notificationId -> {
		};
	}
}
