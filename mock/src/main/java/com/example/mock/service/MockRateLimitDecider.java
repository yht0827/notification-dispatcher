package com.example.mock.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.example.mock.api.ChannelType;
import com.example.mock.config.MockProperties;
import com.example.mock.service.dto.MockSendContext;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MockRateLimitDecider {

	private static final String ERROR_CODE_RATE_LIMIT = "MOCK_RATE_LIMIT";

	private final MockProperties properties;
	private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

	public void check(MockSendContext context) {
		if (!properties.getRateLimit().isEnabled()) {
			return;
		}

		int limitPerSecond = resolveLimitPerSecond(context.channelType());
		if (limitPerSecond <= 0) {
			return;
		}

		String channelKey = normalizeChannel(context.channelType());
		long epochSecond = Instant.now().getEpochSecond();
		int currentCount = incrementAndGet(channelKey, epochSecond);
		if (currentCount <= limitPerSecond) {
			return;
		}

		throw new MockFailureException(
			HttpStatus.TOO_MANY_REQUESTS,
			ERROR_CODE_RATE_LIMIT,
			"Mock rate limit exceeded",
			context.requestId(),
			context.channelType(),
			context.receiver(),
			context.messageLength(),
			context.startedAtMillis(),
			properties.getRateLimit().getRetryAfterSeconds()
		);
	}

	private int resolveLimitPerSecond(ChannelType channelType) {
		MockProperties.ChannelConfig channelConfig = properties.getChannels().get(normalizeChannel(channelType));
		if (channelConfig != null && channelConfig.getRateLimitPerSecond() != null) {
			return channelConfig.getRateLimitPerSecond();
		}
		return properties.getRateLimit().getDefaultPerSecond();
	}

	private int incrementAndGet(String channelKey, long epochSecond) {
		final int[] currentCount = new int[1];
		counters.compute(channelKey, (key, counter) -> {
			if (counter == null || counter.epochSecond() != epochSecond) {
				currentCount[0] = 1;
				return new WindowCounter(epochSecond, 1);
			}
			currentCount[0] = counter.count() + 1;
			return new WindowCounter(epochSecond, currentCount[0]);
		});
		return currentCount[0];
	}

	private String normalizeChannel(ChannelType channelType) {
		return channelType == null ? "unknown" : channelType.name().toLowerCase();
	}

	private record WindowCounter(long epochSecond, int count) {
	}
}
