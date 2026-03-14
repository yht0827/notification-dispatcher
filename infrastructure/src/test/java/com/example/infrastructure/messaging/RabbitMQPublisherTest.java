package com.example.infrastructure.messaging;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.outbound.RabbitMQPublisher;
import com.example.infrastructure.messaging.payload.NotificationMessagePayload;

@ExtendWith(MockitoExtension.class)
class RabbitMQPublisherTest {

	@Mock
	private RabbitTemplate rabbitTemplate;

	private RabbitMQPublisher publisher;

	@BeforeEach
	void setUp() {
		NotificationRabbitProperties properties = new NotificationRabbitProperties(
			"notification.work",
			"notification.work.exchange",
			"notification.wait",
			"notification.dlq",
			"notification.dlq.exchange",
			3, 5000, 1, 10, 1,
			null,
			false, 50, 200, 0.0d
		);
		publisher = new RabbitMQPublisher(rabbitTemplate, properties);
	}

	@Test
	@DisplayName("notificationId로 work exchange에 메시지를 발행한다")
	void publish_sendsToWorkExchange() {
		// when
		publisher.publish(42L);

		// then
		verify(rabbitTemplate).convertAndSend(
			eq("notification.work.exchange"),
			eq("notification.work"),
			any(NotificationMessagePayload.class)
		);
	}

	@Test
	@DisplayName("RabbitTemplate 예외가 전파된다")
	void publish_propagatesException() {
		doThrow(new RuntimeException("connection refused"))
			.when(rabbitTemplate)
			.convertAndSend(anyString(), anyString(), any(NotificationMessagePayload.class));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> publisher.publish(1L))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("connection refused");
	}
}
