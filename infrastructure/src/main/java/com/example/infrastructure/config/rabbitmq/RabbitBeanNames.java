package com.example.infrastructure.config.rabbitmq;

public final class RabbitBeanNames {

	private RabbitBeanNames() {
	}

	public static final String LISTENER_CONTAINER_FACTORY = "rabbitListenerContainerFactory";
	public static final String BATCH_LISTENER_CONTAINER_FACTORY = "rabbitBatchListenerContainerFactory";
	public static final String LISTENER_TASK_EXECUTOR = "rabbitListenerTaskExecutor";
}
