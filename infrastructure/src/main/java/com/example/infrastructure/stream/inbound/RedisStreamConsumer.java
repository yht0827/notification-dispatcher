package com.example.infrastructure.stream.inbound;

import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;

import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.config.StreamKeyType;
import com.example.infrastructure.stream.exception.NonRetryableStreamMessageException;
import com.example.infrastructure.stream.exception.RetryableStreamMessageException;
import com.example.infrastructure.stream.outbound.RedisStreamDlqPublisher;
import com.example.infrastructure.stream.outbound.RedisStreamWaitPublisher;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RedisStreamConsumer implements StreamListener<String, ObjectRecord<String, NotificationStreamPayload>> {

	private final StringRedisTemplate redisTemplate;
	private final RedisStreamRecordHandler recordHandler;
	private final RedisStreamDlqPublisher dlqPublisher;
	private final RedisStreamWaitPublisher waitPublisher;
	private final NotificationStreamProperties properties;

	@Override
	public void onMessage(ObjectRecord<String, NotificationStreamPayload> record) {
		NotificationStreamPayload payload = record.getValue();
		int retryCount = payload.getRetryCount();
		Long notificationId = null;

		try {
			notificationId = resolveNotificationId(payload);
			recordHandler.process(notificationId, retryCount);
			acknowledge(record.getId());
			log.debug("메시지 ACK 완료: recordId={}, notificationId={}", record.getId(), notificationId);
		} catch (RuntimeException e) {
			handleFailure(record, payload, notificationId, retryCount, e);
		}
	}

	private void handleFailure(ObjectRecord<String, NotificationStreamPayload> record,
		NotificationStreamPayload payload,
		Long notificationId,
		int retryCount,
		RuntimeException exception) {
		if (exception instanceof NonRetryableStreamMessageException nonRetryable) {
			publishToDeadLetter(record, payload, notificationId, nonRetryable.getMessage());
			return;
		}

		if (exception instanceof RetryableStreamMessageException retryable) {
			publishToWait(record, notificationId, retryCount, retryable.getMessage());
			return;
		}

		log.error("예상치 못한 예외로 메시지 처리 실패: recordId={}, notificationId={}, reason={}",
			record.getId(), notificationId, exception.getMessage(), exception);
		throw exception;
	}

	private Long resolveNotificationId(NotificationStreamPayload payload) {
		if (payload == null) {
			throw new NonRetryableStreamMessageException("payload 값이 비어 있습니다.");
		}

		try {
			return payload.notificationIdAsLong();
		} catch (NumberFormatException e) {
			throw new NonRetryableStreamMessageException(
				"notificationId 형식이 올바르지 않습니다: " + payload.getNotificationId(),
				e
			);
		}
	}

	private void publishToDeadLetter(ObjectRecord<String, NotificationStreamPayload> record,
		NotificationStreamPayload payload,
		Long notificationId,
		String reason) {
		dlqPublisher.publish(record.getId(), payload, notificationId, reason);
		acknowledge(record.getId());

		log.warn("재시도 불필요 메시지 DLQ 전송: recordId={}, notificationId={}, reason={}",
			record.getId(), notificationId, reason);
	}

	private void publishToWait(ObjectRecord<String, NotificationStreamPayload> record,
		Long notificationId,
		int retryCount,
		String reason) {
		waitPublisher.publish(notificationId, retryCount, reason);
		acknowledge(record.getId());

		log.info("WAIT 스트림 이동: recordId={}, notificationId={}, retryCount={}, reason={}",
			record.getId(), notificationId, retryCount, reason);
	}

	private void acknowledge(RecordId recordId) {
		redisTemplate.opsForStream()
			.acknowledge(properties.resolveKey(StreamKeyType.WORK), properties.consumerGroup(), recordId);
	}
}
