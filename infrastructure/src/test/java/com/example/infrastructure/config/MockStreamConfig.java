package com.example.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.infrastructure.config.stream.NotificationStreamConfig;

@Configuration
@ConditionalOnProperty(name = NotificationStreamConfig.STREAM_ENABLED_PROPERTY, havingValue = "false")
public class MockStreamConfig {

	@Bean
	public NotificationEventPublisher notificationEventPublisher() {
		return notificationId -> {
		};
	}
}
