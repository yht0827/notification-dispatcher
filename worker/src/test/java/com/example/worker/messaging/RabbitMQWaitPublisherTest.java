package com.example.worker.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.worker.config.rabbitmq.NotificationRabbitProperties;
import com.example.worker.messaging.outbound.RabbitMQWaitPublisher;
import com.example.worker.messaging.payload.NotificationMessagePayload;

@ExtendWith(MockitoExtension.class)
class RabbitMQWaitPublisherTest {

	@Mock
	private RabbitTemplate rabbitTemplate;

	private RabbitMQWaitPublisher waitPublisher;

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
		waitPublisher = new RabbitMQWaitPublisher(rabbitTemplate, properties);
	}

	@Test
	@DisplayName("retryable 메시지를 WAIT exchange로 발행하고 TTL을 설정한다")
	void publish_sendsWaitMessageWithExpiration() {
		waitPublisher.publish(42L, 1, "temporary error");

		ArgumentCaptor<NotificationMessagePayload> payloadCaptor = ArgumentCaptor.forClass(NotificationMessagePayload.class);
		ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);

		verify(rabbitTemplate).convertAndSend(
			eq("notification.wait.exchange"),
			eq("notification.wait"),
			payloadCaptor.capture(),
			postProcessorCaptor.capture()
		);

		NotificationMessagePayload streamPayload = payloadCaptor.getValue();
		assertThat(streamPayload.notificationId()).isEqualTo(42L);
		assertThat(streamPayload.retryCount()).isEqualTo(2);

		Message message = new Message(new byte[0], new MessageProperties());
		postProcessorCaptor.getValue().postProcessMessage(message);
		assertThat(message.getMessageProperties().getExpiration()).isEqualTo("10000");
	}

	@Test
	@DisplayName("음수 retryCount 입력 시 0으로 정규화 후 다음 재시도 횟수를 계산한다")
	void publish_normalizesNegativeRetryCount() {
		waitPublisher.publish(7L, -3, null);

		ArgumentCaptor<NotificationMessagePayload> payloadCaptor = ArgumentCaptor.forClass(NotificationMessagePayload.class);
		verify(rabbitTemplate).convertAndSend(
			eq("notification.wait.exchange"),
			eq("notification.wait"),
			payloadCaptor.capture(),
			org.mockito.ArgumentMatchers.any(MessagePostProcessor.class)
		);

		NotificationMessagePayload streamPayload = payloadCaptor.getValue();
		assertThat(streamPayload.retryCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("retryAfterMillis가 있으면 계산된 백오프 대신 그대로 사용한다")
	void publish_usesRetryAfterDelayWhenProvided() {
		waitPublisher.publish(9L, 1, "429 too many requests", 30_000L);

		ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
		verify(rabbitTemplate).convertAndSend(
			eq("notification.wait.exchange"),
			eq("notification.wait"),
			org.mockito.ArgumentMatchers.any(NotificationMessagePayload.class),
			postProcessorCaptor.capture()
		);

		Message message = new Message(new byte[0], new MessageProperties());
		postProcessorCaptor.getValue().postProcessMessage(message);
		assertThat(message.getMessageProperties().getExpiration()).isEqualTo("30000");
	}

	@Test
	@DisplayName("지터가 설정되면 재시도 TTL이 범위 내에서 랜덤화된다")
	void publish_appliesJitterWhenConfigured() {
		NotificationRabbitProperties jitterProperties = new NotificationRabbitProperties(
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
			0.2d
		);
		RabbitMQWaitPublisher jitterPublisher = new RabbitMQWaitPublisher(rabbitTemplate, jitterProperties);

		AtomicLong expiration = new AtomicLong();
		jitterPublisher.publish(11L, 1, "temporary error");

		ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
		verify(rabbitTemplate).convertAndSend(
			eq("notification.wait.exchange"),
			eq("notification.wait"),
			org.mockito.ArgumentMatchers.any(NotificationMessagePayload.class),
			postProcessorCaptor.capture()
		);
		Message message = new Message(new byte[0], new MessageProperties());
		postProcessorCaptor.getValue().postProcessMessage(message);
		expiration.set(Long.parseLong(message.getMessageProperties().getExpiration()));

		assertThat(expiration.get()).isBetween(8_000L, 12_000L);
	}
}
