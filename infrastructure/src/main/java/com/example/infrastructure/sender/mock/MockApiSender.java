package com.example.infrastructure.sender.mock;

import org.springframework.stereotype.Component;

import com.example.application.port.out.NotificationSender.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.infrastructure.sender.mock.caller.MockApiCaller;
import com.example.infrastructure.sender.mock.config.MockApiProperties;
import com.example.infrastructure.sender.mock.dto.MockApiSendRequest;
import com.example.infrastructure.sender.mock.exception.MockApiNonRetryableException;
import com.example.infrastructure.sender.mock.exception.MockApiRetryableException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
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
			log.info("mock API 발송 성공: notificationId={}, channel={}", notification.getId(), channelType);
			return result;
		} catch (RequestNotPermitted e) {
			return fail(notification.getId(), channelType, "rate limit 초과");
		} catch (CallNotPermittedException e) {
			return fail(notification.getId(), channelType, "circuit breaker OPEN");
		} catch (MockApiNonRetryableException | MockApiRetryableException e) {
			return fail(notification.getId(), channelType, e.getMessage());
		} catch (RuntimeException e) {
			return fail(notification.getId(), channelType, "알 수 없는 오류: " + e.getMessage());
		}
	}

	private SendResult fail(Long notificationId, ChannelType channelType, String reason) {
		log.warn("mock API 발송 실패: notificationId={}, channel={}, reason={}", notificationId, channelType, reason);
		return SendResult.fail(reason);
	}
}
