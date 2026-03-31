package com.example.worker.messaging.payload;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationWaitPayloadTest {

	@Test
	@DisplayName("WAIT payload는 retry 정보를 정규화하고 message payload로 변환한다")
	void from_normalizesRetryAndConvertsToMessagePayload() {
		NotificationWaitPayload payload = NotificationWaitPayload.from(10L, -1, 5000L, null);

		assertThat(payload.notificationId()).isEqualTo(10L);
		assertThat(payload.currentRetryCount()).isZero();
		assertThat(payload.nextRetryCount()).isEqualTo(1);
		assertThat(payload.delayMillis()).isEqualTo(5000L);
		assertThat(payload.lastError()).isEmpty();

		NotificationMessagePayload streamPayload = payload.toMessagePayload();
		assertThat(streamPayload.notificationId()).isEqualTo(10L);
		assertThat(streamPayload.retryCount()).isEqualTo(1);
	}
}
