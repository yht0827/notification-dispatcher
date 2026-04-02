package com.example.worker.messaging;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.worker.messaging.outbound.RabbitMQPublisher;
import com.example.worker.messaging.payload.NotificationMessagePayload;
import com.example.worker.support.NotificationRabbitPropertiesFixtures;

@ExtendWith(MockitoExtension.class)
class RabbitMQPublisherTest {

	@Mock
	private RabbitTemplate rabbitTemplate;

	private RabbitMQPublisher publisher;

	@BeforeEach
	void setUp() {
		publisher = new RabbitMQPublisher(rabbitTemplate, NotificationRabbitPropertiesFixtures.defaultProperties());
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
