package com.example.api.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationQueryRequestTest {

	@Test
	@DisplayName("NotificationGroupQueryRequest size가 null이면 기본값 20을 반환한다")
	void groupQueryResolveSize_returnsDefaultWhenNull() {
		NotificationGroupQueryRequest request = new NotificationGroupQueryRequest("client", null, null);

		assertThat(request.resolveSize()).isEqualTo(20);
	}

	@Test
	@DisplayName("NotificationGroupQueryRequest size가 있으면 지정값을 반환한다")
	void groupQueryResolveSize_returnsExplicitValue() {
		NotificationGroupQueryRequest request = new NotificationGroupQueryRequest("client", 10L, 50);

		assertThat(request.resolveSize()).isEqualTo(50);
	}
}
