package com.example.mock.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.mock.api.ChannelType;
import com.example.mock.api.MockSendFailResponse;
import com.example.mock.api.MockSendRequest;
import com.example.mock.api.MockSendSuccessResponse;
import com.example.mock.config.MockMode;
import com.example.mock.config.MockProperties;
import com.example.mock.service.dto.MockFailResult;
import com.example.mock.service.dto.MockFailureLog;
import com.example.mock.service.dto.MockSendContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockSendService {

	private static final String RESULT_SUCCESS = "SUCCESS";
	private static final String RESULT_FAIL = "FAIL";
	private static final String UNKNOWN = "UNKNOWN";

	private final MockProperties properties;
	private final MockBehaviorDecider behaviorDecider;
	private final MockRateLimitDecider rateLimitDecider;

	public MockSendSuccessResponse handleSend(MockSendRequest request) {
		MockSendContext context = MockSendContext.from(request, nowMillis());
		MockMode mode = resolveMode(context.channelType());

		// Provider-side rate limiting should reject immediately before adding latency/failure noise.
		rateLimitDecider.check(context);

		boolean delayed = behaviorDecider.shouldDelay(mode);
		if (delayed) {
			behaviorDecider.applyDelay(context);
		}

		if (behaviorDecider.shouldFail(mode)) {
			throw behaviorDecider.buildFailureException(context);
		}

		long latencyMs = elapsedSince(context.startedAtMillis());
		logSuccess(context, delayed ? "DELAY" : RESULT_SUCCESS, latencyMs);

		return new MockSendSuccessResponse(
			RESULT_SUCCESS,
			context.requestId(),
			safeChannel(context.channelType()),
			nowUtc(),
			latencyMs
		);
	}

	public MockSendFailResponse buildFailResponse(MockFailResult failResult) {
		long latencyMs = elapsedSince(failResult.startedAtMillis());
		return new MockSendFailResponse(
			RESULT_FAIL,
			safeText(failResult.requestId()),
			safeChannel(failResult.channelType()),
			failResult.errorCode(),
			failResult.message(),
			nowUtc(),
			latencyMs
		);
	}

	public void logFailure(MockFailureLog failureLog) {
		log.info(
			"mock_send requestId={} channelType={} receiver={} messageLength={} chosenBehavior=FAIL httpStatus={} latencyMs={}",
			safeText(failureLog.requestId()),
			safeChannel(failureLog.channelType()),
			safeText(failureLog.receiver()),
			failureLog.messageLength(),
			failureLog.httpStatus(),
			failureLog.latencyMs()
		);
	}

	private void logSuccess(MockSendContext context, String behavior, long latencyMs) {
		if (properties.getLog().isIncludeMaskedMessagePreview()) {
			log.info(
				"mock_send requestId={} channelType={} receiver={} messageLength={} messagePreview={} chosenBehavior={} httpStatus={} latencyMs={}",
				context.requestId(),
				safeChannel(context.channelType()),
				context.receiver(),
				context.messageLength(),
				maskedPreview(context.messageOrEmpty()),
				behavior,
				HttpStatus.OK.value(),
				latencyMs
			);
			return;
		}

		log.info(
			"mock_send requestId={} channelType={} receiver={} messageLength={} chosenBehavior={} httpStatus={} latencyMs={}",
			context.requestId(),
			safeChannel(context.channelType()),
			context.receiver(),
			context.messageLength(),
			behavior,
			HttpStatus.OK.value(),
			latencyMs
		);
	}

	private MockMode resolveMode(ChannelType channelType) {
		if (channelType != null && channelType != ChannelType.UNKNOWN) {
			MockProperties.ChannelConfig channelConfig = properties.getChannels()
				.get(channelType.name().toLowerCase());
			if (channelConfig != null && channelConfig.getMode() != null) {
				return channelConfig.getMode();
			}
		}
		return properties.getMode();
	}

	private String maskedPreview(String message) {
		if (message.isBlank()) {
			return "";
		}
		int maxLength = properties.getLog().getMessagePreviewLength();
		return "*".repeat(Math.min(message.length(), maxLength));
	}

	private String safeText(String value) {
		return value == null || value.isBlank() ? UNKNOWN : value;
	}

	private String safeChannel(ChannelType channelType) {
		return channelType == null ? UNKNOWN : channelType.name();
	}

	private String nowUtc() {
		return OffsetDateTime.now(ZoneOffset.UTC).toString();
	}

	private long elapsedSince(long startedAtMillis) {
		return Math.max(0L, nowMillis() - startedAtMillis);
	}

	private long nowMillis() {
		return System.currentTimeMillis();
	}
}
