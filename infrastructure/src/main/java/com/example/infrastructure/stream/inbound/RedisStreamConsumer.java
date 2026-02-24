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
		Long notificationId = null;
		int retryCount = 0;

		try {
			// WORK
			notificationId = extractNotificationId(payload);
			retryCount = payload.getRetryCount();
			recordHandler.process(notificationId, retryCount);
			handleSuccess(record.getId(), notificationId);
		} catch (NonRetryableStreamMessageException e) {
			// DEAD
			handleNonRetryableException(record, notificationId, e);
		} catch (RetryableStreamMessageException e) {
			// WAIT
			handleRetryableException(record, notificationId, retryCount, e);
		} catch (Exception e) {
			log.error("예상치 못한 예외로 메시지 처리 실패: recordId={}, notificationId={}, reason={}",
				record.getId(), notificationId, e.getMessage(), e);
			throw e;
		}
	}

	private Long extractNotificationId(NotificationStreamPayload payload) {
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

	private void handleSuccess(RecordId recordId, Long notificationId) {
		acknowledge(recordId);
		log.debug("메시지 ACK 완료: recordId={}, notificationId={}", recordId, notificationId);
	}

	private void handleNonRetryableException(ObjectRecord<String, NotificationStreamPayload> record,
		Long notificationId, NonRetryableStreamMessageException e) {

		dlqPublisher.publish(record.getId(), record.getValue(), notificationId, e.getMessage());
		acknowledge(record.getId());

		log.warn("재시도 불필요 메시지 DLQ 전송: recordId={}, notificationId={}, reason={}",
			record.getId(), notificationId, e.getMessage());
	}

	private void handleRetryableException(ObjectRecord<String, NotificationStreamPayload> record,
		Long notificationId, int retryCount, Exception e) {

		waitPublisher.publish(notificationId, retryCount, e.getMessage());
		acknowledge(record.getId());

		log.info("WAIT 스트림 이동: recordId={}, notificationId={}, retryCount={}, reason={}",
			record.getId(), notificationId, retryCount, e.getMessage());
	}

	private void acknowledge(RecordId recordId) {
		redisTemplate.opsForStream()
			.acknowledge(properties.resolveKey(StreamKeyType.WORK), properties.consumerGroup(), recordId);
	}
}
