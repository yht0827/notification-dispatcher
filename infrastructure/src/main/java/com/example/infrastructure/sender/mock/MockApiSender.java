package com.example.infrastructure.sender.mock;

import org.springframework.stereotype.Component;

import com.example.application.port.out.result.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.infrastructure.sender.mock.caller.MockApiCaller;
import com.example.infrastructure.sender.mock.config.MockApiProperties;
import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.exception.MockApiNonRetryableException;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockApiSender {
	private final MockApiCaller mockApiCaller;
	private final MockApiProperties properties;

	public SendResult send(Notification n, ChannelType channelType) {
		if (!properties.isEnabled()) {
			log.debug("mock API 비활성화: notificationId={}", n.getId());
			return SendResult.success();
		}

		try {
			return mockApiCaller.call(MockApiSendRequest.from(n, channelType));
		} catch (MockApiNonRetryableException e) {
			log.debug("mock API 예외(재시도 불가) 매핑: notificationId={}, channel={}, reason={}", n.getId(), channelType,
				e.getMessage());
			return failNonRetryable(e.getMessage());
		} catch (MockApiRetryableException e) {
			log.debug("mock API 예외(재시도 가능) 매핑: notificationId={}, channel={}, reason={}", n.getId(), channelType,
				e.getMessage());
			return failRetryable(e.getMessage());
		}
	}

	private SendResult failRetryable(String reason) {
		return SendResult.failRetryable(reason);
	}

	private SendResult failNonRetryable(String reason) {
		return SendResult.failNonRetryable(reason);
	}
}
