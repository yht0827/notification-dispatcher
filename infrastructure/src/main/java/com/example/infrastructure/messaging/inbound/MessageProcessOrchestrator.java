package com.example.infrastructure.messaging.inbound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.infrastructure.messaging.exception.NonRetryableMessageException;
import com.example.infrastructure.messaging.exception.RetryableMessageException;
import com.example.infrastructure.messaging.payload.NotificationMessagePayload;
import com.example.infrastructure.messaging.port.DeadLetterPublisher;
import com.example.infrastructure.messaging.port.WaitPublisher;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MessageProcessOrchestrator {

	private static final String METRIC_DISPATCH_RESULT = "notification.dispatch.result";
	private static final String TAG_OUTCOME = "outcome";

	private final RabbitMQRecordHandler recordHandler;
	private final DeadLetterPublisher dlqPublisher;
	private final WaitPublisher waitPublisher;
	private final MeterRegistry meterRegistry;

	public MessageProcessDecision process(MessageProcessContext context) {
		if (context.isInvalid()) {
			publishToDeadLetter(context.sourceRecordId(), context.payload(), null, context.invalidReason());
			return ackWith(context.deliveryTag(), "dlq");
		}

		NotificationMessagePayload payload = context.payload();
		Long notificationId = null;
		int retryCount = 0;

		try {
			notificationId = validatePayload(payload);
			retryCount = payload.getRetryCount();
			recordHandler.process(notificationId, retryCount);
			log.debug("메시지 ACK 완료: notificationId={}, retryCount={}", notificationId, retryCount);
			return ackWith(context.deliveryTag(), "success");
		} catch (NonRetryableMessageException exception) {
			publishToDeadLetter(context.sourceRecordId(), payload, notificationId, exception.getMessage());
			log.warn("재시도 불필요 메시지 DLQ 전송: notificationId={}, reason={}", notificationId, exception.getMessage());
			return ackWith(context.deliveryTag(), "dlq");
		} catch (RetryableMessageException exception) {
			publishToWait(notificationId, retryCount, exception.getMessage(), exception.retryDelayMillis());
			log.info("WAIT 큐 이동: notificationId={}, retryCount={}, reason={}",
				notificationId, retryCount, exception.getMessage());
			return ackWith(context.deliveryTag(), "wait");
		} catch (RuntimeException exception) {
			log.error("예상치 못한 예외: notificationId={}, reason={}", notificationId, exception.getMessage(), exception);
			return nackWith(context.deliveryTag());
		}
	}

	public List<MessageProcessDecision> processBatch(List<MessageProcessContext> contexts) {
		if (contexts == null || contexts.isEmpty()) {
			return List.of();
		}

		List<MessageProcessDecision> decisions = new ArrayList<>(contexts.size());
		List<RecordProcessRequest> requests = new ArrayList<>();
		Map<Long, MessageProcessContext> contextByDeliveryTag = new LinkedHashMap<>();

		for (MessageProcessContext context : contexts) {
			if (context.isInvalid()) {
				publishToDeadLetter(context.sourceRecordId(), context.payload(), null, context.invalidReason());
				decisions.add(MessageProcessDecision.ack(context.deliveryTag()));
				continue;
			}

			NotificationMessagePayload payload = context.payload();
			if (payload == null || payload.getNotificationId() == null) {
				publishToDeadLetter(context.sourceRecordId(), payload, null, "payload 또는 notificationId 값이 비어 있습니다.");
				decisions.add(MessageProcessDecision.ack(context.deliveryTag()));
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
				decisions.add(MessageProcessDecision.nack(request.contextId()));
			}
		}
		return decisions;
	}

	private MessageProcessDecision toDecision(MessageProcessContext context, RecordProcessResult result) {
		if (result.isSuccess() || result.isSkipped()) {
			return ackWith(context.deliveryTag(), "success");
		}
		if (result.isNonRetryableFailure()) {
			publishToDeadLetter(context.sourceRecordId(), context.payload(), result.notificationId(), result.reason());
			return ackWith(context.deliveryTag(), "dlq");
		}
		if (result.isRetryableFailure()) {
			publishToWait(result.notificationId(), result.retryCount(), result.reason(), result.retryDelayMillis());
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

	private void publishToWait(Long notificationId, int retryCount, String reason, Long retryDelayMillis) {
		waitPublisher.publish(notificationId, retryCount, reason, retryDelayMillis);
	}
}
