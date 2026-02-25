package com.example.infrastructure.stream.inbound;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.infrastructure.config.NotificationStreamProperties;
import com.example.infrastructure.config.StreamKeyType;
import com.example.infrastructure.stream.payload.NotificationStreamPayload;
import com.example.infrastructure.stream.payload.NotificationWaitPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RedisStreamWaitScheduler {

	private static final int BATCH_SIZE = 100;

	private final StringRedisTemplate redisTemplate;
	private final NotificationStreamProperties properties;

	@Scheduled(fixedDelayString = "${notification.stream.wait-poll-interval-millis:1000}")
	public void processWaitingMessages() {
		// WAIT 스트림 조회
		List<MapRecord<String, Object, Object>> records = readWaitingRecords();
		if (records == null || records.isEmpty()) {
			return;
		}

		int republished = 0;
		for (MapRecord<String, Object, Object> record : records) {
			// WORK 스트림 재 발행
			if (tryRepublish(record)) {
				republished++;
			}
		}

		if (republished > 0) {
			log.info("WAIT → WORK 재발행 완료: count={}", republished);
		}
	}

	private List<MapRecord<String, Object, Object>> readWaitingRecords() {
		try {
			String waitKey = properties.resolveKey(StreamKeyType.WAIT);
			if (waitKey == null) {
				log.warn("WAIT 스트림 키가 설정되지 않음");
				return null;
			}
			return redisTemplate.opsForStream().range(
				waitKey,
				Range.closed("-", "+"),
				Limit.limit().count(BATCH_SIZE)
			);
		} catch (Exception e) {
			log.error("WAIT 스트림 조회 실패: reason={}", e.getMessage(), e);
			return null;
		}
	}

	private boolean tryRepublish(MapRecord<String, Object, Object> record) {
		NotificationWaitPayload payload = mapToPayload(record.getValue());

		// 재시도 시간이 아직 안 됐으면 건너뛴다.
		if (payload == null || System.currentTimeMillis() < payload.getNextRetryAt()) {
			return false;
		}

		try {
			// WORK 스트림 재발행 하면서 retryCount를 1 증가
			republishToWork(payload.notificationIdAsLong(), payload.getRetryCount() + 1);

			// 재발행 성공 시 WAIT 스트림 원본을 삭제해 중복 재처리를 방지
			redisTemplate.opsForStream().delete(properties.resolveKey(StreamKeyType.WAIT), record.getId());
			return true;
		} catch (Exception e) {
			log.error("WAIT → WORK 재발행 실패: notificationId={}, reason={}",
				payload.getNotificationId(), e.getMessage());
			return false;
		}
	}

	private NotificationWaitPayload mapToPayload(Map<Object, Object> map) {
		try {
			String notificationId = String.valueOf(map.get("notificationId"));
			int retryCount = Integer.parseInt(String.valueOf(map.getOrDefault("retryCount", "0")));
			long nextRetryAt = Long.parseLong(String.valueOf(map.getOrDefault("nextRetryAt", "0")));
			String lastError = String.valueOf(map.getOrDefault("lastError", ""));
			return new NotificationWaitPayload(notificationId, retryCount, nextRetryAt, lastError);
		} catch (Exception e) {
			log.warn("WAIT 페이로드 변환 실패: map={}, reason={}", map, e.getMessage());
			return null;
		}
	}

	private void republishToWork(Long notificationId, int retryCount) {
		ObjectRecord<String, NotificationStreamPayload> record = StreamRecords
			.objectBacked(new NotificationStreamPayload(notificationId, retryCount))
			.withStreamKey(properties.resolveKey(StreamKeyType.WORK));
		redisTemplate.opsForStream().add(record);
	}
}
