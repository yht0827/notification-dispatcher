package com.example.infrastructure.messaging.inbound;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

import com.example.infrastructure.config.rabbitmq.RabbitBeanNames;
import com.example.infrastructure.messaging.exception.NonRetryableMessageException;
import com.example.infrastructure.messaging.exception.RetryableMessageException;
import com.example.infrastructure.messaging.payload.NotificationMessagePayload;
import com.example.infrastructure.messaging.port.DeadLetterPublisher;
import com.example.infrastructure.messaging.port.WaitPublisher;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQConsumer {

	private final RabbitMQRecordHandler recordHandler;
	private final DeadLetterPublisher dlqPublisher;
	private final WaitPublisher waitPublisher;

	@RabbitListener(
		queues = "${notification.rabbitmq.work-queue}",
		containerFactory = RabbitBeanNames.LISTENER_CONTAINER_FACTORY
	)
	public void onMessage(NotificationMessagePayload payload, Message message, Channel channel,
		@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		String sourceRecordId = resolveSourceRecordId(message, deliveryTag);
		Long notificationId = null;
		int retryCount = 0;

		try {
			notificationId = validatePayload(payload);
			retryCount = payload.getRetryCount();
			recordHandler.process(notificationId, retryCount);
			channel.basicAck(deliveryTag, false);
			log.debug("메시지 ACK 완료: notificationId={}, retryCount={}", notificationId, retryCount);
		} catch (NonRetryableMessageException e) {
			publishToDeadLetter(sourceRecordId, payload, notificationId, e.getMessage());
			channel.basicAck(deliveryTag, false);
			log.warn("재시도 불필요 메시지 DLQ 전송: notificationId={}, reason={}", notificationId, e.getMessage());
		} catch (RetryableMessageException e) {
			publishToWait(notificationId, retryCount, e.getMessage());
			channel.basicAck(deliveryTag, false);
			log.info("WAIT 큐 이동: notificationId={}, retryCount={}, reason={}", notificationId, retryCount,
				e.getMessage());
		} catch (RuntimeException e) {
			log.error("예상치 못한 예외: notificationId={}, reason={}", notificationId, e.getMessage(), e);
			channel.basicNack(deliveryTag, false, false);
		}
	}

	private Long validatePayload(NotificationMessagePayload payload) {
		if (payload == null || payload.getNotificationId() == null) {
			throw new NonRetryableMessageException("payload 또는 notificationId 값이 비어 있습니다.");
		}
		return payload.getNotificationId();
	}

	private void publishToDeadLetter(String sourceRecordId, NotificationMessagePayload payload, Long notificationId,
		String reason) {
		dlqPublisher.publish(sourceRecordId, payload, notificationId, reason);
	}

	private void publishToWait(Long notificationId, int retryCount, String reason) {
		waitPublisher.publish(notificationId, retryCount, reason);
	}

	private String resolveSourceRecordId(Message message, long deliveryTag) {
		if (message != null && message.getMessageProperties() != null) {
			String messageId = message.getMessageProperties().getMessageId();
			if (messageId != null && !messageId.isBlank()) {
				return messageId;
			}
		}
		return String.valueOf(deliveryTag);
	}
}
