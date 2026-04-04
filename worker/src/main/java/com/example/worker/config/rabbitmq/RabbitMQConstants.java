package com.example.worker.config.rabbitmq;

public final class RabbitMQConstants {

	private RabbitMQConstants() {
	}

	// Property keys
	public static final String MESSAGING_ENABLED = "notification.messaging.enabled";

	// Bean names
	public static final String SINGLE_LISTENER_CONTAINER_FACTORY = "rabbitSingleListenerContainerFactory";
	public static final String LISTENER_TASK_EXECUTOR = "rabbitListenerTaskExecutor";
}
