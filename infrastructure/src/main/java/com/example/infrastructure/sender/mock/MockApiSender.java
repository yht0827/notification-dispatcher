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
			log.debug("mock API 비활성화 상태로 발송 성공 처리: notificationId={}", notification.getId());
			return SendResult.success();
		}

		try {
			return mockApiCaller.call(MockApiSendRequest.from(notification, channelType));
		} catch (RequestNotPermitted e) {
			return SendResult.fail("외부 API rate limit 초과");
		} catch (CallNotPermittedException e) {
			return SendResult.fail("외부 API circuit breaker OPEN 상태");
		} catch (MockApiNonRetryableException | MockApiRetryableException e) {
			return SendResult.fail(e.getMessage());
		} catch (RuntimeException e) {
			return SendResult.fail("외부 API 호출 실패: " + e.getMessage());
		}
	}
}
