package com.example.worker.sender.mock;

import com.example.application.port.out.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.worker.sender.ChannelSender;
import com.example.worker.sender.mock.config.MockApiProperties;
import com.example.worker.sender.mock.dto.MockApiSendRequest;
import com.example.worker.sender.mock.http.ChannelMockApiCaller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractMockChannelSender implements ChannelSender {

	private final ChannelMockApiCaller caller;
	private final MockApiProperties properties;
	private final ChannelType channelType;

	@Override
	public ChannelType getChannelType() {
		return channelType;
	}

	@Override
	public SendResult send(Notification notification) {
		if (!properties.isEnabled()) {
			log.debug("mock API 비활성화: notificationId={}", notification.getId());
			return SendResult.success();
		}

		SendResult result = caller.call(MockApiSendRequest.from(notification, channelType));
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
