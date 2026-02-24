package com.example.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationDispatchUseCase;
import com.example.application.port.out.NotificationRepository;
import com.example.application.port.out.NotificationSender;
import com.example.application.port.out.NotificationSender.SendResult;
import com.example.domain.notification.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService implements NotificationDispatchUseCase {

	private final NotificationRepository notificationRepository;
	private final NotificationSender notificationSender;

	@Override
	@Transactional
	public DispatchResult dispatch(Notification notification) {
		if (notification.isTerminal()) {
			log.debug("이미 종결 상태인 알림 발송 생략: id={}, status={}",
				notification.getId(), notification.getStatus());
			return DispatchResult.success();
		}

		// PENDING → SENDING
		notification.startSending();
		Notification managedNotification = notificationRepository.save(notification);

		// API 전송
		SendResult sendResult = notificationSender.send(managedNotification);

		if (sendResult.isSuccess()) {
			// SENDING → SENT
			managedNotification.markAsSent();
			notificationRepository.save(managedNotification);
			log.info("알림 발송 성공: id={}, receiver={}", managedNotification.getId(), managedNotification.getReceiver());
			return DispatchResult.success();
		} else {
			log.warn("알림 발송 실패: id={}, reason={}", managedNotification.getId(), sendResult.failReason());
			return DispatchResult.fail(sendResult.failReason());
		}
	}

	@Override
	@Transactional
	public void markAsFailed(Long notificationId, String reason) {
		// SENDING → FAILED
		notificationRepository.findById(notificationId).ifPresent(notification -> {
			notification.markAsFailed(reason);
			notificationRepository.save(notification);
			log.error("알림 최종 실패: id={}, reason={}", notificationId, reason);
		});
	}
}
