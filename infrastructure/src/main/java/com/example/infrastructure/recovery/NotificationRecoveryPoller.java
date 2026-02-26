package com.example.infrastructure.recovery;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;

import com.example.application.port.out.NotificationEventPublisher;
import com.example.application.port.out.NotificationRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;
import com.example.infrastructure.config.RecoveryProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 다운 시 DB에 PENDING 상태로 남아있는 Notification을 복구한다.
 * - PENDING 상태 + N분 경과한 Notification 조회
 * - Redis Stream에 재발행
 */
@Slf4j
@RequiredArgsConstructor
public class NotificationRecoveryPoller {

	private final NotificationRepository notificationRepository;
	private final NotificationEventPublisher streamPublisher;
	private final RecoveryProperties recoveryProperties;

	@Scheduled(fixedDelayString = "${recovery.poll-interval-millis:300000}")
	public void recoverStuckNotifications() {
		LocalDateTime threshold = LocalDateTime.now()
			.minusMinutes(recoveryProperties.resolveThresholdMinutes());

		List<Notification> stuckNotifications = notificationRepository.findByStatusAndCreatedAtBefore(
			NotificationStatus.PENDING,
			threshold,
			recoveryProperties.resolveBatchSize()
		);

		if (stuckNotifications.isEmpty()) {
			return;
		}

		int recovered = 0;
		for (Notification notification : stuckNotifications) {
			if (publishIfPossible(notification.getId())) {
				recovered++;
			}
		}

		if (recovered > 0) {
			log.info("PENDING 상태 Notification 복구 완료: recovered={}, total={}",
				recovered, stuckNotifications.size());
		}
	}

	private boolean publishIfPossible(Long notificationId) {
		try {
			streamPublisher.publish(notificationId);
			return true;
		} catch (Exception e) {
			log.warn("Notification 복구 발행 실패: notificationId={}, reason={}",
				notificationId, e.getMessage());
			return false;
		}
	}
}
