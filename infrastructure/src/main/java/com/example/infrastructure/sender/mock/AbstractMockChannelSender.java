package com.example.infrastructure.sender.mock;

import com.example.application.port.out.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.infrastructure.sender.ChannelSender;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractMockChannelSender implements ChannelSender {

	private final MockApiSender mockApiSender;
	private final ChannelType channelType;

	@Override
	public ChannelType getChannelType() {
		return channelType;
	}

	@Override
	public SendResult send(Notification notification) {
		SendResult result = mockApiSender.send(notification, channelType);
		String channel = channelType.name();

		if (result.isSuccess()) {
			log.info("[{}] mock API 발송 성공: notificationId={}, to={}", channel, notification.getId(), notification.getReceiver());
			return result;
		}

		log.warn("[{}] mock API 발송 실패: notificationId={}, to={}, reason={}",
			channel, notification.getId(), notification.getReceiver(), result.failReason());
		return result;
	}
}
