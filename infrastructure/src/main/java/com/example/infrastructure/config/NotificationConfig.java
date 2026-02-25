package com.example.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(name = NotificationConfig.STREAM_ENABLED_PROPERTY, havingValue = "true")
@Import({
	NotificationStreamComponentsConfig.class,
	NotificationStreamListenerConfig.class
})
public class NotificationConfig {

	public static final String STREAM_ENABLED_PROPERTY = "notification.stream.enabled";
}
