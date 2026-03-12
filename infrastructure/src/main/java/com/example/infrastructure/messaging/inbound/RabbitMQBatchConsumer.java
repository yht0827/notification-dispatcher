package com.example.infrastructure.messaging.inbound;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;

import com.example.infrastructure.config.rabbitmq.RabbitBeanNames;
import com.example.infrastructure.messaging.outbound.DeadLetterPublisher;
import com.example.infrastructure.messaging.outbound.WaitPublisher;
import com.example.infrastructure.messaging.payload.NotificationMessagePayload;
import com.rabbitmq.client.Channel;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RabbitMQBatchConsumer {

	private static final String METRIC_DISPATCH_RESULT = "notification.dispatch.result";
	private static final String TAG_OUTCOME = "outcome";

	private final RabbitMQRecordHandler recordHandler;
	private final DeadLetterPublisher dlqPublisher;
	private final WaitPublisher waitPublisher;
	private final MeterRegistry meterRegistry;
	private final MessageConverter messageConverter;

	@RabbitListener(
		queues = "${notification.rabbitmq.work-queue}",
		containerFactory = RabbitBeanNames.BATCH_LISTENER_CONTAINER_FACTORY
	)
	public void onMessages(List<Message> messages, Channel channel) throws IOException {
		List<MessageProcessContext> contexts = messages.stream()
			.map(message -> MessageProcessContext.fromAmqpMessage(message, messageConverter))
			.toList();

		List<MessageProcessDecision> decisions = processBatch(contexts);

		for (MessageProcessDecision decision : decisions) {
			if (decision.shouldAck()) {
				channel.basicAck(decision.deliveryTag(), false);
				continue;
			}
			channel.basicNack(decision.deliveryTag(), false, false);
		}
	}

	private List<MessageProcessDecision> processBatch(List<MessageProcessContext> contexts) {
		if (contexts == null || contexts.isEmpty()) {
			return List.of();
		}

		List<MessageProcessDecision> decisions = new ArrayList<>(contexts.size());
		List<RecordProcessRequest> requests = new ArrayList<>();
		Map<Long, MessageProcessContext> contextByDeliveryTag = new LinkedHashMap<>();

		for (MessageProcessContext context : contexts) {
			if (context.isInvalid()) {
				dlqPublisher.publish(context.sourceRecordId(), context.payload(), null, context.invalidReason());
				decisions.add(ackWith(context.deliveryTag(), "dlq"));
				continue;
			}

			NotificationMessagePayload payload = context.payload();
			if (payload == null || payload.getNotificationId() == null) {
				dlqPublisher.publish(context.sourceRecordId(), payload, null, "payload 또는 notificationId 값이 비어 있습니다.");
				decisions.add(ackWith(context.deliveryTag(), "dlq"));
				continue;
			}

			RecordProcessRequest request = new RecordProcessRequest(
				context.deliveryTag(),
				payload.getNotificationId(),
				payload.getRetryCount()
			);
			requests.add(request);
			contextByDeliveryTag.put(context.deliveryTag(), context);
		}

		if (requests.isEmpty()) {
			return decisions;
		}

		try {
			List<RecordProcessResult> results = recordHandler.processBatch(requests);
			for (RecordProcessResult result : results) {
				MessageProcessContext context = contextByDeliveryTag.get(result.contextId());
				if (context == null) {
					continue;
				}
				decisions.add(toDecision(context, result));
			}
		} catch (RuntimeException exception) {
			log.error("배치 처리 중 예기치 못한 오류", exception);
			for (RecordProcessRequest request : requests) {
				decisions.add(nackWith(request.contextId()));
			}
		}
		return decisions;
	}

	private MessageProcessDecision toDecision(MessageProcessContext context, RecordProcessResult result) {
		if (result.isSuccess() || result.isSkipped()) {
			return ackWith(context.deliveryTag(), "success");
		}
		if (result.isNonRetryableFailure()) {
			dlqPublisher.publish(context.sourceRecordId(), context.payload(), result.notificationId(), result.reason());
			return ackWith(context.deliveryTag(), "dlq");
		}
		if (result.isRetryableFailure()) {
			waitPublisher.publish(result.notificationId(), result.retryCount(), result.reason(), result.retryDelayMillis());
			return ackWith(context.deliveryTag(), "wait");
		}
		return nackWith(context.deliveryTag());
	}

	private MessageProcessDecision ackWith(long deliveryTag, String outcome) {
		meterRegistry.counter(METRIC_DISPATCH_RESULT, TAG_OUTCOME, outcome).increment();
		return MessageProcessDecision.ack(deliveryTag);
	}

	private MessageProcessDecision nackWith(long deliveryTag) {
		meterRegistry.counter(METRIC_DISPATCH_RESULT, TAG_OUTCOME, "nack").increment();
		return MessageProcessDecision.nack(deliveryTag);
	}
}
