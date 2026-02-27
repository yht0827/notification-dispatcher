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
import com.example.infrastructure.stream.payload.NotificationStreamPayload;
import com.example.infrastructure.stream.port.WaitPublisher;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RedisStreamInitializer {
	private static final String BUSYGROUP_CODE = "BUSYGROUP";

	private final StringRedisTemplate redisTemplate;
	private final WaitPublisher waitPublisher;
	private final NotificationStreamProperties properties;

	@PostConstruct
	public void init() {
		createConsumerGroupIfNotExists(); // 컨슈머 그룹 생성
		recoverPendingMessages(); // 미처리 메시지 복구
	}

	private void createConsumerGroupIfNotExists() {
		String workKey = properties.resolveKey(StreamKeyType.WORK);
		String group = properties.consumerGroup();

		try {
			redisTemplate.opsForStream().createGroup(workKey, group);
			log.info("Consumer Group 생성 완료: streamKey={}, group={}", workKey, group);
		} catch (RuntimeException e) {
			if (isBusyGroupError(e)) {
				log.debug("Consumer Group 이미 존재: streamKey={}, group={}", workKey, group);
			} else {
				log.warn("Consumer Group 생성 실패: streamKey={}, group={}, reason={}",
					workKey, group, mostSpecificMessage(e));
			}
		}
	}

	private boolean isBusyGroupError(Throwable throwable) {
		return mostSpecificMessage(throwable).contains(BUSYGROUP_CODE);
	}

	private String mostSpecificMessage(Throwable throwable) {
		Throwable current = throwable;
		String message = throwable.getMessage();

		while (current != null) {
			if (current.getMessage() != null && !current.getMessage().isBlank()) {
				message = current.getMessage();
			}
			current = current.getCause();
		}

		return message != null ? message : throwable.getClass().getSimpleName();
	}

	private void recoverPendingMessages() {
		// XPENDING로 메시지 ID만 반환
		PendingMessages pending = readPendingMessages();
		if (pending == null || pending.isEmpty()) {
			return;
		}

		int recovered = 0;
		for (PendingMessage pm : pending) {
			if (recoverSinglePending(pm.getId())) {
				recovered++;
			}
		}
		if (recovered > 0) {
			log.info("시작 시 Pending 메시지 복구 완료: count={}", recovered);
		}
	}

	private PendingMessages readPendingMessages() {
		try {

			// XPENDING으로 Pending 메시지 조회
			return redisTemplate.opsForStream().pending(
				properties.resolveKey(StreamKeyType.WORK), // stream key
				properties.consumerGroup(), // consumer group
				Range.closed("-", "+"), // 전체 범위
				properties.resolveWaitBatchSize() // 100 (배치 크기)
			);
		} catch (RuntimeException e) {
			log.warn("Pending 메시지 조회 실패: reason={}", mostSpecificMessage(e));
			return null;
		}
	}

	private boolean recoverSinglePending(RecordId recordId) {
		try {
			// 내용(payload)을 얻으려면 XRANGE로 다시 조회 필요
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

			waitPublisher.publish(payload.getNotificationId(), payload.getRetryCount(), "시작 시 Pending 복구");

			// XACK - 메시지 ACK 처리
			redisTemplate.opsForStream()
				.acknowledge(properties.resolveKey(StreamKeyType.WORK), properties.consumerGroup(), record.getId());
			return true;
		} catch (RuntimeException e) {
			log.warn("Pending 메시지 복구 실패: recordId={}, reason={}", recordId, mostSpecificMessage(e));
			return false;
		}
	}
}
