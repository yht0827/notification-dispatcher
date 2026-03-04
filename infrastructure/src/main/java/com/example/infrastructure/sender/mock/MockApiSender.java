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

	public SendResult send(Notification notification, ChannelType channelType) {
		if (!properties.isEnabled()) {
			log.debug("mock API 비활성화: notificationId={}", notification.getId());
			return SendResult.success();
		}

		try {
			SendResult result = mockApiCaller.call(MockApiSendRequest.from(notification, channelType));
			if (result.isSuccess()) {
				log.info("mock API 발송 성공: notificationId={}, channel={}", notification.getId(), channelType);
			} else {
				log.warn("mock API 발송 실패: notificationId={}, channel={}, reason={}",
					notification.getId(), channelType, result.failReason());
			}
			return result;
		} catch (MockApiNonRetryableException e) {
			return failNonRetryable(notification.getId(), channelType, e.getMessage());
		} catch (MockApiRetryableException e) {
			return failRetryable(notification.getId(), channelType, e.getMessage());
		} catch (RuntimeException e) {
			return failRetryable(notification.getId(), channelType, "알 수 없는 오류: " + e.getMessage());
		}
	}

	private SendResult failRetryable(Long notificationId, ChannelType channelType, String reason) {
		log.warn("mock API 발송 실패: notificationId={}, channel={}, reason={}", notificationId, channelType, reason);
		return SendResult.failRetryable(reason);
	}

	private SendResult failNonRetryable(Long notificationId, ChannelType channelType, String reason) {
		log.warn("mock API 발송 실패(재시도 불가): notificationId={}, channel={}, reason={}", notificationId, channelType, reason);
		return SendResult.failNonRetryable(reason);
	}
}
