package com.example.infrastructure.recovery;

import java.time.Duration;
import java.util.List;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.config.RecoveryProperties;
import com.example.infrastructure.config.StreamKeyType;
import com.example.infrastructure.stream.outbound.RedisStreamWaitPublisher;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PendingMessageReclaimer {

	private final StringRedisTemplate redisTemplate;
	private final NotificationStreamProperties streamProperties;
	private final RecoveryProperties recoveryProperties;
	private final RedisStreamWaitPublisher waitPublisher;

	@Scheduled(fixedDelayString = "${recovery.claim-interval-millis:60000}")
	public void reclaimIdleMessages() {
		String workKey = streamProperties.resolveKey(StreamKeyType.WORK);
		String group = streamProperties.consumerGroup();
		Duration minIdleTime = recoveryProperties.resolveClaimMinIdleTime();

		// XPENDING로 메시지 ID만 반환
		List<PendingMessage> idleMessages = findIdlePendingMessages(workKey, group, minIdleTime);
		if (idleMessages.isEmpty()) {
			return;
		}

		int reclaimed = 0;
		for (PendingMessage pm : idleMessages) {
			if (reclaimMessage(workKey, group, pm.getId())) {
				reclaimed++;
			}
		}

		if (reclaimed > 0) {
			log.info("PEL 메시지 회수 완료: reclaimed={}, total={}", reclaimed, idleMessages.size());
		}
	}

	private List<PendingMessage> findIdlePendingMessages(String workKey, String group, Duration minIdleTime) {
		try {
			// XPENDING으로 idle 메시지 조회
			PendingMessages allPending = redisTemplate.opsForStream().pending(
				workKey,
				group,
				Range.closed("-", "+"),
				recoveryProperties.resolveBatchSize()
			);

			if (allPending.isEmpty()) {
				return List.of();
			}

			return allPending.stream()
				.filter(pm -> pm.getElapsedTimeSinceLastDelivery().compareTo(minIdleTime) >= 0)
				.toList();
		} catch (RuntimeException e) {
			log.warn("Pending 메시지 조회 실패: reason={}", e.getMessage());
			return List.of();
		}
	}

	private boolean reclaimMessage(String workKey, String group, RecordId recordId) {
		try {
			// XRANGE로 메시지 내용 조회
			List<ObjectRecord<String, NotificationStreamPayload>> records = redisTemplate.opsForStream().range(
				NotificationStreamPayload.class,
				workKey,
				Range.closed(recordId.getValue(), recordId.getValue())
			);

			if (records.isEmpty()) {
				return false;
			}

			ObjectRecord<String, NotificationStreamPayload> record = records.getFirst();
			NotificationStreamPayload payload = record.getValue();

			// Wait Stream으로 재발행하여 재처리
			waitPublisher.publish(payload.getNotificationId(), payload.getRetryCount(), "PEL 회수");

			// ACK 처리 - PEL에서 제거
			redisTemplate.opsForStream().acknowledge(workKey, group, record.getId());
			return true;
		} catch (RuntimeException e) {
			log.warn("PEL 메시지 회수 실패: recordId={}, reason={}", recordId, e.getMessage());
			return false;
		}
	}
}
