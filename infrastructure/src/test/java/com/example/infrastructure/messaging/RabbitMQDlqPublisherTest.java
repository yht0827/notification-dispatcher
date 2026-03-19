package com.example.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.messaging.outbound.RabbitMQDlqPublisher;
import com.example.infrastructure.messaging.payload.NotificationDeadLetterPayload;

@ExtendWith(MockitoExtension.class)
class RabbitMQDlqPublisherTest {

	@Mock
	private RabbitTemplate rabbitTemplate;

	private RabbitMQDlqPublisher dlqPublisher;

	@BeforeEach
	void setUp() {
		NotificationRabbitProperties properties = new NotificationRabbitProperties(
			"notification.work",
			"notification.work.exchange",
			"notification.wait",
			"notification.dlq",
			"notification.dlq.exchange",
			3,
			5000,
			1,
			10,
			1,
			null,
			0.0d
		);
		dlqPublisher = new RabbitMQDlqPublisher(rabbitTemplate, properties);
	}

	@Test
	@DisplayName("DLQ payload 기본값을 정규화해 fanout exchange로 발행한다")
	void publish_normalizesDefaultValues() {
		dlqPublisher.publish(null, null, null, null);

		ArgumentCaptor<NotificationDeadLetterPayload> payloadCaptor =
			ArgumentCaptor.forClass(NotificationDeadLetterPayload.class);

		verify(rabbitTemplate).convertAndSend(
			eq("notification.dlq.exchange"),
			eq(""),
			payloadCaptor.capture()
		);

		NotificationDeadLetterPayload payload = payloadCaptor.getValue();
		assertThat(payload.recordId()).isEqualTo("n/a");
		assertThat(payload.notificationId()).isEqualTo("unknown");
		assertThat(payload.payload()).isEmpty();
		assertThat(payload.reason()).isEmpty();
		assertThat(payload.failedAt()).isNotBlank();
	}

	@Test
	@DisplayName("DLQ payload는 입력값을 그대로 반영해 발행한다")
	void publish_keepsProvidedValues() {
		dlqPublisher.publish("record-1", "raw-payload", 55L, "send failed");

		ArgumentCaptor<NotificationDeadLetterPayload> payloadCaptor =
			ArgumentCaptor.forClass(NotificationDeadLetterPayload.class);

		verify(rabbitTemplate).convertAndSend(
			eq("notification.dlq.exchange"),
			eq(""),
			payloadCaptor.capture()
		);

		NotificationDeadLetterPayload payload = payloadCaptor.getValue();
		assertThat(payload.recordId()).isEqualTo("record-1");
		assertThat(payload.notificationId()).isEqualTo("55");
		assertThat(payload.payload()).isEqualTo("raw-payload");
		assertThat(payload.reason()).isEqualTo("send failed");
	}
}
