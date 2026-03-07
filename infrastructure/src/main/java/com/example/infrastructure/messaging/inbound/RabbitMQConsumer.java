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
		Long notiId = null;
		int retryCount = 0;

		try {
			// 성공
			notiId = validatePayload(payload);
			retryCount = payload.getRetryCount();
			recordHandler.process(notiId, retryCount);
			channel.basicAck(deliveryTag, false);

			log.debug("메시지 ACK 완료: notificationId={}, retryCount={}", notiId, retryCount);
		} catch (NonRetryableMessageException e) {
			// 비 재시도 오류 → DLQ
			publishToDeadLetter(sourceRecordId, payload, notiId, e.getMessage());
			channel.basicAck(deliveryTag, false);

			log.warn("재시도 불필요 메시지 DLQ 전송: notificationId={}, reason={}", notiId, e.getMessage());
		} catch (RetryableMessageException e) {
			// 재시도 필요 → Wait 큐
			publishToWait(notiId, retryCount, e.getMessage());
			channel.basicAck(deliveryTag, false);

			log.info("WAIT 큐 이동: notificationId={}, retryCount={}, reason={}", notiId, retryCount, e.getMessage());
		} catch (RuntimeException e) {
			// 예상 외 오류 → Nack (DLQ 자동 이동)
			log.error("예상치 못한 예외: notificationId={}, reason={}", notiId, e.getMessage(), e);
			channel.basicNack(deliveryTag, false, false);
			// false=requeue하지 않음 → x-dead-letter-exchange로 라우팅
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
