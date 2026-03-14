package com.example.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationStatusTest {

	@Test
	@DisplayName("SENDING 상태는 허용된 전이만 true다")
	void sendingTransitionMatrix() {
		assertThat(NotificationStatus.SENDING.canTransitionTo(NotificationStatus.SENDING)).isTrue();
		assertThat(NotificationStatus.SENDING.canTransitionTo(NotificationStatus.SENT)).isTrue();
		assertThat(NotificationStatus.SENDING.canTransitionTo(NotificationStatus.FAILED)).isTrue();
		assertThat(NotificationStatus.SENDING.canTransitionTo(NotificationStatus.PENDING)).isFalse();
		assertThat(NotificationStatus.SENDING.canTransitionTo(NotificationStatus.CANCELED)).isFalse();
	}

	@Test
	@DisplayName("terminal 상태 여부는 종료 상태와 비종료 상태를 구분한다")
	void terminalStatusMatrix() {
		assertThat(NotificationStatus.PENDING.isTerminal()).isFalse();
		assertThat(NotificationStatus.SENDING.isTerminal()).isFalse();
		assertThat(NotificationStatus.SENT.isTerminal()).isTrue();
		assertThat(NotificationStatus.FAILED.isTerminal()).isTrue();
		assertThat(NotificationStatus.CANCELED.isTerminal()).isTrue();
	}
}
