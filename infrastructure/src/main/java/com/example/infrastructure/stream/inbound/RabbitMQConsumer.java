package com.example.infrastructure.stream.inbound;

import com.example.infrastructure.config.rabbitmq.NotificationRabbitProperties;
import com.example.infrastructure.stream.exception.NonRetryableStreamMessageException;
import com.example.infrastructure.stream.exception.RetryableStreamMessageException;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;
import com.example.infrastructure.stream.port.DeadLetterPublisher;
import com.example.infrastructure.stream.port.WaitPublisher;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQConsumer {

	private final RabbitMQRecordHandler recordHandler;
	private final DeadLetterPublisher dlqPublisher;
	private final WaitPublisher waitPublisher;
	private final NotificationRabbitProperties properties;

	@RabbitListener(queues = "#{notificationRabbitProperties.workQueue()}", containerFactory = "rabbitListenerContainerFactory")
	public void onMessage(NotificationStreamPayload payload, Message message, Channel channel,
						  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
		Long notificationId = null;
		int retryCount = 0;

		try {
			notificationId = validatePayload(payload);
			retryCount = payload.getRetryCount();
			recordHandler.process(notificationId, retryCount);
			channel.basicAck(deliveryTag, false);
			log.debug("메시지 ACK 완료: notificationId={}, retryCount={}", notificationId, retryCount);
		} catch (NonRetryableStreamMessageException e) {
			publishToDeadLetter(payload, notificationId, e.getMessage());
			channel.basicAck(deliveryTag, false);
			log.warn("재시도 불필요 메시지 DLQ 전송: notificationId={}, reason={}", notificationId, e.getMessage());
		} catch (RetryableStreamMessageException e) {
			publishToWait(notificationId, retryCount, e.getMessage());
			channel.basicAck(deliveryTag, false);
			log.info("WAIT 큐 이동: notificationId={}, retryCount={}, reason={}", notificationId, retryCount, e.getMessage());
		} catch (RuntimeException e) {
			log.error("예상치 못한 예외: notificationId={}, reason={}", notificationId, e.getMessage(), e);
			channel.basicNack(deliveryTag, false, false);
		}
	}

	private Long validatePayload(NotificationStreamPayload payload) {
		if (payload == null || payload.getNotificationId() == null) {
			throw new NonRetryableStreamMessageException("payload 또는 notificationId 값이 비어 있습니다.");
		}
		return payload.getNotificationId();
	}

	private void publishToDeadLetter(NotificationStreamPayload payload, Long notificationId, String reason) {
		dlqPublisher.publish(null, payload, notificationId, reason);
	}

	private void publishToWait(Long notificationId, int retryCount, String reason) {
		waitPublisher.publish(notificationId, retryCount, reason);
	}
}
