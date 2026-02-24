package com.example.infrastructure.stream.inbound;

import java.util.List;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.config.StreamKeyType;
import com.example.infrastructure.stream.outbound.RedisStreamWaitPublisher;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RedisStreamInitializer {

	private final StringRedisTemplate redisTemplate;
	private final RedisStreamWaitPublisher waitPublisher;
	private final NotificationStreamProperties properties;

	@PostConstruct
	public void init() {
		createConsumerGroupIfNotExists(); // 컨슈머 그룹 생성
		recoverPendingMessages(); // 미처리 메시지 복구
	}

	private void createConsumerGroupIfNotExists() {
		try {
			redisTemplate.opsForStream().createGroup(properties.resolveKey(StreamKeyType.WORK), properties.consumerGroup());
			log.info("Consumer Group 생성 완료: streamKey={}, group={}", properties.resolveKey(StreamKeyType.WORK), properties.consumerGroup());
		} catch (Exception e) {
			if (containsErrorCode(e)) {
				log.debug("Consumer Group 이미 존재: streamKey={}, group={}", properties.resolveKey(StreamKeyType.WORK), properties.consumerGroup());
			} else {
				log.warn("Consumer Group 생성 실패: streamKey={}, group={}, reason={}",
					properties.resolveKey(StreamKeyType.WORK), properties.consumerGroup(), extractMostSpecificMessage(e));
			}
		}
	}

	private boolean containsErrorCode(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			String message = current.getMessage();
			if (message != null && message.contains("BUSYGROUP")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private String extractMostSpecificMessage(Throwable throwable) {
		Throwable current = throwable;
		String fallback = throwable.getMessage();

		while (current != null) {
			if (current.getMessage() != null && !current.getMessage().isBlank()) {
				fallback = current.getMessage();
			}
			current = current.getCause();
		}

		return fallback != null ? fallback : throwable.getClass().getSimpleName();
	}

	private void recoverPendingMessages() {
		try {
			PendingMessages pending = redisTemplate.opsForStream().pending(
				properties.resolveKey(StreamKeyType.WORK), properties.consumerGroup(), Range.closed("-", "+"), 100);

			if (pending.isEmpty()) {
				return;
			}

			int recovered = 0;
			for (PendingMessage pm : pending) {
				if (recoverSinglePending(pm.getId())) {
					recovered++;
				}
			}
			log.info("시작 시 Pending 메시지 복구 완료: count={}", recovered);
		} catch (Exception e) {
			log.warn("Pending 메시지 복구 실패: reason={}", e.getMessage());
		}
	}

	private boolean recoverSinglePending(RecordId recordId) {
		try {
			List<ObjectRecord<String, NotificationStreamPayload>> records = redisTemplate.opsForStream().range(
				NotificationStreamPayload.class,
				properties.resolveKey(StreamKeyType.WORK),
				Range.closed(recordId.getValue(), recordId.getValue())
			);

			if (records.isEmpty()) {
				return false;
			}

			ObjectRecord<String, NotificationStreamPayload> record = records.getFirst();
			NotificationStreamPayload payload = record.getValue();

			waitPublisher.publish(payload.notificationIdAsLong(), payload.getRetryCount(), "시작 시 Pending 복구");
			redisTemplate.opsForStream().acknowledge(properties.resolveKey(StreamKeyType.WORK), properties.consumerGroup(), record.getId());
			return true;
		} catch (Exception e) {
			log.warn("Pending 메시지 복구 실패: recordId={}, reason={}", recordId, e.getMessage());
			return false;
		}
	}
}
