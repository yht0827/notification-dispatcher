package com.example.mock.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Validated
@ConfigurationProperties(prefix = "mock")
@Getter
@Setter
public class MockProperties {

	@NotNull
	private MockMode mode = MockMode.RANDOM;

	private Map<String, ChannelConfig> channels = new HashMap<>();

	@Valid
	private final Latency latency = new Latency();
	@Valid
	private final Failure failure = new Failure();
	@Valid
	private final Log log = new Log();

	@Getter
	@Setter
	public static class ChannelConfig {
		private MockMode mode;  // null이면 전역 mode 사용
	}

	@Getter
	@Setter
	public static class Latency {
		private boolean enabled = true;
		private double probability = 0.15d;
		@Min(0)
		private int minMs = 300;
		@Min(1)
		private int maxMs = 2500;
	}

	@Getter
	@Setter
	public static class Failure {
		private boolean enabled = true;
		private double probability = 0.10d;
		private List<Integer> types = new ArrayList<>(List.of(500, 503, 429));
		@Min(1)
		private int retryAfterSeconds = 15;
	}

	@Getter
	@Setter
	public static class Log {
		private boolean includeMaskedMessagePreview = false;
		@Min(1)
		@Max(200)
		private int messagePreviewLength = 20;
	}
}
