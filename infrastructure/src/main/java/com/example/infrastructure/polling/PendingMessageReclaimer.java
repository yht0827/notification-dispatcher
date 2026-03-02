package com.example.infrastructure.polling;

import java.util.Collections;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;

import com.example.infrastructure.config.stream.NotificationStreamProperties;
import com.example.infrastructure.stream.StreamKeyType;
import com.example.infrastructure.stream.port.WaitPublisher;

import io.lettuce.core.Consumer;
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStreamCommands;
import io.lettuce.core.models.stream.ClaimedMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PendingMessageReclaimer {

	private final StatefulRedisConnection<String, String> lettuceConnection;
	private final NotificationStreamProperties streamProperties;
	private final RecoveryProperties recoveryProperties;
	private final WaitPublisher waitPublisher;

	@Scheduled(fixedDelayString = "${recovery.claim-interval-millis:60000}")
	public void reclaimIdleMessages() {
		List<ClaimedMessage> claimedMessages = executeAutoClaim();
		if (claimedMessages.isEmpty()) {
			return;
		}

		int reclaimed = processClaimedMessages(claimedMessages);
		if (reclaimed > 0) {
			log.info("PEL 메시지 회수 완료 (XAUTOCLAIM): reclaimed={}, total={}", reclaimed, claimedMessages.size());
		}
	}

	private int processClaimedMessages(List<ClaimedMessage> messages) {
		String workKey = streamProperties.resolveKey(StreamKeyType.WORK);
		String group = streamProperties.consumerGroup();

		return (int)messages.stream()
			.filter(message -> processAndAcknowledge(workKey, group, message))
			.count();
	}

	private boolean processAndAcknowledge(String workKey, String group, ClaimedMessage message) {
		try {
			Long notificationId = message.notificationId().orElse(null);
			if (notificationId == null) {
				log.warn("notificationId 추출 실패: recordId={}", message.id());
				return false;
			}

			waitPublisher.publish(notificationId, message.retryCount(), "PEL 회수 (XAUTOCLAIM)");
			lettuceConnection.sync().xack(workKey, group, message.id());
			return true;
		} catch (RuntimeException e) {
			log.warn("PEL 메시지 처리 실패: recordId={}, reason={}", message.id(), e.getMessage());
			return false;
		}
	}

	private List<ClaimedMessage> executeAutoClaim() {
		try {
			return doAutoClaim(lettuceConnection.sync());
		} catch (RuntimeException e) {
			log.warn("XAUTOCLAIM 실패: reason={}", e.getMessage());
			return Collections.emptyList();
		}
	}

	private List<ClaimedMessage> doAutoClaim(RedisStreamCommands<String, String> commands) {
		String workKey = streamProperties.resolveKey(StreamKeyType.WORK);
		String group = streamProperties.consumerGroup();
		String consumer = streamProperties.consumerName();

		XAutoClaimArgs<String> args = new XAutoClaimArgs<String>()
			.minIdleTime(recoveryProperties.resolveClaimMinIdleTime())
			.startId("0-0")
			.count(recoveryProperties.resolveBatchSize())
			.consumer(Consumer.from(group, consumer));

		ClaimedMessages<String, String> result = commands.xautoclaim(workKey, args);
		if (result == null || result.getMessages().isEmpty()) {
			return Collections.emptyList();
		}

		return result.getMessages().stream()
			.map(ClaimedMessage::from)
			.toList();
	}
}
