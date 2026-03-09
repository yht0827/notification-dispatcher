package com.example.application.port.out.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationGroupCountUpdateTest {

	@Test
	@DisplayName("group count delta 값을 그대로 보관한다")
	void storesCountDeltas() {
		NotificationGroupCountUpdate update = new NotificationGroupCountUpdate(10L, 3, 1);

		assertThat(update.groupId()).isEqualTo(10L);
		assertThat(update.sentDelta()).isEqualTo(3);
		assertThat(update.failedDelta()).isEqualTo(1);
	}
}
