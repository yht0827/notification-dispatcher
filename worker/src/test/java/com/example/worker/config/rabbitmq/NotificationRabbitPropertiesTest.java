package com.example.worker.config.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.worker.support.NotificationRabbitPropertiesFixtures;

class NotificationRabbitPropertiesTest {

	@Test
	@DisplayName("maxRetryCount가 0 이하이면 기본값 3회를 사용한다")
	void resolveMaxRetryCount_fallsBackToDefault() {
		NotificationRabbitProperties properties = NotificationRabbitPropertiesFixtures.propertiesWithMaxRetryCount(0);

		assertThat(properties.resolveMaxRetryCount()).isEqualTo(3);
	}

	@Test
	@DisplayName("maxRetryCount가 양수이면 설정값을 그대로 사용한다")
	void resolveMaxRetryCount_returnsConfiguredValue() {
		NotificationRabbitProperties properties = NotificationRabbitPropertiesFixtures.propertiesWithMaxRetryCount(5);

		assertThat(properties.resolveMaxRetryCount()).isEqualTo(5);
	}
}
