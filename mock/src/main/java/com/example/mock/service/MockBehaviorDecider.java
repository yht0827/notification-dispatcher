package com.example.mock.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.example.mock.config.MockMode;
import com.example.mock.config.MockProperties;
import com.example.mock.service.dto.MockFailureSpec;
import com.example.mock.service.dto.MockSendContext;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MockBehaviorDecider {

	private static final String ERROR_CODE_INTERNAL = "MOCK_INTERNAL";
	private static final String ERROR_CODE_UNAVAILABLE = "MOCK_UNAVAILABLE";
	private static final String ERROR_CODE_RATE_LIMIT = "MOCK_RATE_LIMIT";

	private final MockProperties properties;

	public boolean shouldDelay(MockMode mode) {
		return switch (mode) {
			case ALWAYS_DELAY -> true;
			case RANDOM -> properties.getLatency().isEnabled()
				&& randomHit(properties.getLatency().getProbability());
			default -> false;
		};
	}

	public boolean shouldFail(MockMode mode) {
		return switch (mode) {
			case ALWAYS_FAIL -> true;
			case RANDOM -> properties.getFailure().isEnabled()
				&& randomHit(properties.getFailure().getProbability());
			default -> false;
		};
	}

	public void applyDelay(MockSendContext context) {
		int min = properties.getLatency().getMinMs();
		int max = properties.getLatency().getMaxMs();
		int delayMs = max <= min ? min : ThreadLocalRandom.current().nextInt(min, max + 1);

		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw buildInterruptedDelayException(context);
		}
	}

	public MockFailureException buildFailureException(MockSendContext context) {
		MockFailureSpec failureSpec = selectFailureSpec(properties.getFailure().getTypes());
		return new MockFailureException(
			failureSpec.status(),
			failureSpec.errorCode(),
			"Mock failure generated intentionally",
			context.requestId(),
			context.channelType(),
			context.receiver(),
			context.messageLength(),
			context.startedAtMillis()
		);
	}

	private boolean randomHit(double probability) {
		return ThreadLocalRandom.current().nextDouble() < probability;
	}

	private MockFailureException buildInterruptedDelayException(MockSendContext context) {
		return new MockFailureException(
			HttpStatus.INTERNAL_SERVER_ERROR,
			ERROR_CODE_INTERNAL,
			"Interrupted while applying mock delay",
			context.requestId(),
			context.channelType(),
			context.receiver(),
			context.messageLength(),
			context.startedAtMillis()
		);
	}

	private MockFailureSpec selectFailureSpec(List<Integer> types) {
		HttpStatus status = resolveStatus(types);
		return new MockFailureSpec(status, resolveErrorCode(status));
	}

	private HttpStatus resolveStatus(List<Integer> types) {
		int statusCode = (types == null || types.isEmpty())
			? HttpStatus.INTERNAL_SERVER_ERROR.value()
			: types.get(ThreadLocalRandom.current().nextInt(types.size()));

		HttpStatus status = HttpStatus.resolve(statusCode);
		return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
	}

	private String resolveErrorCode(HttpStatus status) {
		return switch (status.value()) {
			case 503 -> ERROR_CODE_UNAVAILABLE;
			case 429 -> ERROR_CODE_RATE_LIMIT;
			default -> ERROR_CODE_INTERNAL;
		};
	}
}
