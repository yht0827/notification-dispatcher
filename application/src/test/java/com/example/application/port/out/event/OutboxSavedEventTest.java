package com.example.application.port.out.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxSavedEventTest {

	@Test
	@DisplayName("groupId와 notification ids를 그대로 보관한다")
	void storesGroupIdAndNotificationIds() {
		OutboxSavedEvent event = new OutboxSavedEvent(10L, List.of(1L, 2L, 3L));

		assertThat(event.groupId()).isEqualTo(10L);
		assertThat(event.notificationIds()).containsExactly(1L, 2L, 3L);
	}
}
