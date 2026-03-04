package com.example.infrastructure.sender;

import org.springframework.stereotype.Component;

import com.example.application.port.out.result.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.infrastructure.sender.mock.MockApiSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender implements ChannelSender {

	private final MockApiSender mockApiSender;

	@Override
	public ChannelType getChannelType() {
		return ChannelType.EMAIL;
	}

	@Override
	public SendResult send(Notification notification) {
		SendResult result = mockApiSender.send(notification, ChannelType.EMAIL);
		if (result.isSuccess()) {
			log.info("[EMAIL] mock API 발송 성공: notificationId={}, to={}", notification.getId(), notification.getReceiver());
			return result;
		}

		log.warn("[EMAIL] mock API 발송 실패: notificationId={}, to={}, reason={}",
			notification.getId(), notification.getReceiver(), result.failReason());
		return result;
	}
}
