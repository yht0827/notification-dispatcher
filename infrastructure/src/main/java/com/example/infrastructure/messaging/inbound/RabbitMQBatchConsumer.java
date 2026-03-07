package com.example.infrastructure.messaging.inbound;

import java.io.IOException;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;

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
public class RabbitMQBatchConsumer {

	private final RabbitMQRecordHandler recordHandler;
	private final DeadLetterPublisher dlqPublisher;
	private final WaitPublisher waitPublisher;
	private final MessageConverter messageConverter;

	@RabbitListener(
		queues = "${notification.rabbitmq.work-queue}",
		containerFactory = RabbitBeanNames.BATCH_LISTENER_CONTAINER_FACTORY
	)
	public void onMessages(List<Message> messages, Channel channel) throws IOException {
		for (Message message : messages) {
			long deliveryTag = message.getMessageProperties().getDeliveryTag();
			NotificationMessagePayload payload = toPayload(message);
			handleMessage(payload, message, channel, deliveryTag);
		}
	}

	private void handleMessage(NotificationMessagePayload payload, Message message, Channel channel, long deliveryTag)
		throws IOException {
		String sourceRecordId = resolveSourceRecordId(message, deliveryTag);
		Long notiId = null;
		int retryCount = 0;

		try {
			notiId = validatePayload(payload);
			retryCount = payload.getRetryCount();
			recordHandler.process(notiId, retryCount);
			channel.basicAck(deliveryTag, false);
			log.debug("배치 메시지 ACK 완료: notificationId={}, retryCount={}", notiId, retryCount);
		} catch (NonRetryableMessageException e) {
			dlqPublisher.publish(sourceRecordId, payload, notiId, e.getMessage());
			channel.basicAck(deliveryTag, false);
			log.warn("배치 메시지 DLQ 전송: notificationId={}, reason={}", notiId, e.getMessage());
		} catch (RetryableMessageException e) {
			waitPublisher.publish(notiId, retryCount, e.getMessage());
			channel.basicAck(deliveryTag, false);
			log.info("배치 메시지 WAIT 이동: notificationId={}, retryCount={}, reason={}", notiId, retryCount, e.getMessage());
		} catch (RuntimeException e) {
			log.error("배치 처리 중 예외: notificationId={}, reason={}", notiId, e.getMessage(), e);
			channel.basicNack(deliveryTag, false, false);
		}
	}

	private NotificationMessagePayload toPayload(Message message) {
		Object converted = messageConverter.fromMessage(message);
		if (converted instanceof NotificationMessagePayload payload) {
			return payload;
		}
		throw new NonRetryableMessageException("메시지 payload 변환 실패");
	}

	private Long validatePayload(NotificationMessagePayload payload) {
		if (payload == null || payload.getNotificationId() == null) {
			throw new NonRetryableMessageException("payload 또는 notificationId 값이 비어 있습니다.");
		}
		return payload.getNotificationId();
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

