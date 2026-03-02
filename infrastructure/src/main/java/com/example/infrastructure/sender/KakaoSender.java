package com.example.infrastructure.sender;

import org.springframework.stereotype.Component;

import com.example.application.port.out.NotificationSender.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.infrastructure.sender.mock.MockApiSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoSender implements ChannelSender {

	private final MockApiSender mockApiSender;

	@Override
	public ChannelType getChannelType() {
		return ChannelType.KAKAO;
	}

	@Override
	public SendResult send(Notification notification) {
		SendResult result = mockApiSender.send(notification, ChannelType.KAKAO);
		if (result.isSuccess()) {
			log.info("[KAKAO] mock API 발송 성공: notificationId={}, to={}", notification.getId(), notification.getReceiver());
			return result;
		}

		log.warn("[KAKAO] mock API 발송 실패: notificationId={}, to={}, reason={}",
			notification.getId(), notification.getReceiver(), result.failReason());
		return result;
	}
}
