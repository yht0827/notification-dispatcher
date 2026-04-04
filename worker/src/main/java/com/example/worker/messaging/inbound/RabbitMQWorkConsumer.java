package com.example.worker.messaging.inbound;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.worker.NotificationMetrics;
import com.example.worker.config.rabbitmq.RabbitMQConstants;
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
public class RabbitMQWorkConsumer {

	private final RabbitMQRecordHandler recordHandler;
	private final DeadLetterPublisher dlqPublisher;
	private final WaitPublisher waitPublisher;
	private final MeterRegistry meterRegistry;
	private final MessageConverter messageConverter;

	@RabbitListener(
		queues = "${notification.rabbitmq.work-queue}",
		containerFactory = RabbitMQConstants.SINGLE_LISTENER_CONTAINER_FACTORY
	)
	public void onMessage(Message message, Channel channel) throws IOException {
		// AMQP 메시지 → 컨텍스트 파싱 (payload 역직렬화 포함)
		MessageProcessContext context = MessageProcessContext.fromAmqpMessage(message, messageConverter);

		// 파싱 실패 — 복구 불가, DLQ로 바로 보냄
		if (context.isInvalid()) {
			dlqPublisher.publish(context.sourceRecordId(), context.payload(), null, context.invalidReason());
			channel.basicAck(context.deliveryTag(), false);
			meterRegistry.counter(NotificationMetrics.DISPATCH_RESULT, NotificationMetrics.TAG_OUTCOME, "dlq").increment();
			return;
		}

		// 필수 값 누락 — DLQ
		NotificationMessagePayload payload = context.payload();
		if (payload == null || payload.notificationId() == null) {
			dlqPublisher.publish(context.sourceRecordId(), payload, null, "payload 또는 notificationId 값이 비어 있습니다.");
			channel.basicAck(context.deliveryTag(), false);
			meterRegistry.counter(NotificationMetrics.DISPATCH_RESULT, NotificationMetrics.TAG_OUTCOME, "dlq").increment();
			return;
		}

		// 정상 메시지 — 핸들러에 위임 후 결과에 따라 ACK/NACK 결정
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
			meterRegistry.counter(NotificationMetrics.DISPATCH_RESULT, NotificationMetrics.TAG_OUTCOME, "nack").increment();
		}
	}

	private void applyDecision(Channel channel, MessageProcessContext context, RecordProcessResult r)
		throws IOException {
		if (r.isSuccess() || r.isSkipped()) {
			// 성공 또는 중복 스킵 — ACK
			channel.basicAck(context.deliveryTag(), false);
			meterRegistry.counter(NotificationMetrics.DISPATCH_RESULT, NotificationMetrics.TAG_OUTCOME, "success").increment();
		} else if (r.isNonRetryableFailure()) {
			// 재시도 불가 실패 — DLQ 발행 후 ACK
			dlqPublisher.publish(context.sourceRecordId(), context.payload(), r.notificationId(), r.reason());
			channel.basicAck(context.deliveryTag(), false);
			meterRegistry.counter(NotificationMetrics.DISPATCH_RESULT, NotificationMetrics.TAG_OUTCOME, "dlq").increment();
		} else if (r.isRetryableFailure()) {
			// 일시적 실패 — Wait 큐 발행 후 ACK
			waitPublisher.publish(r.notificationId(), r.retryCount(), r.reason(), r.retryDelayMillis());
			channel.basicAck(context.deliveryTag(), false);
			meterRegistry.counter(NotificationMetrics.DISPATCH_RESULT, NotificationMetrics.TAG_OUTCOME, "wait").increment();
		} else {
			// 예상치 못한 상태 — NACK (재큐잉 없이)
			channel.basicNack(context.deliveryTag(), false, false);
			meterRegistry.counter(NotificationMetrics.DISPATCH_RESULT, NotificationMetrics.TAG_OUTCOME, "nack").increment();
		}
	}
}
