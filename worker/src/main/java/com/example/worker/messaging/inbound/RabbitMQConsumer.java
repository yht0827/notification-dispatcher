package com.example.worker.messaging.inbound;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.worker.config.rabbitmq.RabbitBeanNames;
import com.example.worker.messaging.outbound.DeadLetterPublisher;
import com.example.worker.messaging.outbound.WaitPublisher;
import com.example.worker.messaging.payload.NotificationMessagePayload;
import com.rabbitmq.client.Channel;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQConsumer {

	private static final String METRIC_DISPATCH_RESULT = "notification.dispatch.result";
	private static final String TAG_OUTCOME = "outcome";

	private final RabbitMQRecordHandler recordHandler;
	private final DeadLetterPublisher dlqPublisher;
	private final WaitPublisher waitPublisher;
	private final MeterRegistry meterRegistry;
	private final MessageConverter messageConverter;

	@RabbitListener(
		queues = "${notification.rabbitmq.work-queue}",
		containerFactory = RabbitBeanNames.SINGLE_LISTENER_CONTAINER_FACTORY
	)
	public void onMessage(Message message, Channel channel) throws IOException {
		MessageProcessContext context = MessageProcessContext.fromAmqpMessage(message, messageConverter);

		if (context.isInvalid()) {
			dlqPublisher.publish(context.sourceRecordId(), context.payload(), null, context.invalidReason());
			channel.basicAck(context.deliveryTag(), false);
			meterRegistry.counter(METRIC_DISPATCH_RESULT, TAG_OUTCOME, "dlq").increment();
			return;
		}

		NotificationMessagePayload payload = context.payload();
		if (payload == null || payload.notificationId() == null) {
			dlqPublisher.publish(context.sourceRecordId(), payload, null, "payload 또는 notificationId 값이 비어 있습니다.");
			channel.basicAck(context.deliveryTag(), false);
			meterRegistry.counter(METRIC_DISPATCH_RESULT, TAG_OUTCOME, "dlq").increment();
			return;
		}

		RecordProcessRequest request = new RecordProcessRequest(
			context.deliveryTag(),
			payload.notificationId(),
			payload.retryCount()
		);

		try {
			RecordProcessResult result = recordHandler.process(request);
			applyDecision(channel, context, result);
		} catch (RuntimeException e) {
			log.error("단건 처리 중 예기치 못한 오류", e);
			channel.basicNack(context.deliveryTag(), false, false);
			meterRegistry.counter(METRIC_DISPATCH_RESULT, TAG_OUTCOME, "nack").increment();
		}
	}

	private void applyDecision(Channel channel, MessageProcessContext context, RecordProcessResult r)
		throws IOException {
		if (r.isSuccess() || r.isSkipped()) {

			channel.basicAck(context.deliveryTag(), false);
			meterRegistry.counter(METRIC_DISPATCH_RESULT, TAG_OUTCOME, "success").increment();

		} else if (r.isNonRetryableFailure()) {
			dlqPublisher.publish(context.sourceRecordId(), context.payload(), r.notificationId(), r.reason());

			channel.basicAck(context.deliveryTag(), false);
			meterRegistry.counter(METRIC_DISPATCH_RESULT, TAG_OUTCOME, "dlq").increment();

		} else if (r.isRetryableFailure()) {
			waitPublisher.publish(r.notificationId(), r.retryCount(), r.reason(), r.retryDelayMillis());

			channel.basicAck(context.deliveryTag(), false);
			meterRegistry.counter(METRIC_DISPATCH_RESULT, TAG_OUTCOME, "wait").increment();

		} else {
			channel.basicNack(context.deliveryTag(), false, false);
			meterRegistry.counter(METRIC_DISPATCH_RESULT, TAG_OUTCOME, "nack").increment();
		}
	}
}
