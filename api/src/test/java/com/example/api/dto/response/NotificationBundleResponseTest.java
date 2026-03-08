package com.example.api.dto.response;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationItemResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationStatus;

class NotificationBundleResponseTest {

	@Test
	@DisplayName("알림 그룹 상세 응답은 그룹 내 개별 알림 목록을 포함한다")
	void detailResponseIncludesNotifications() {
		// given
		NotificationGroupDetailResult group = new NotificationGroupDetailResult(
			1L,
			"order-service",
			"MyShop",
			"주문 완료",
			"주문이 정상 처리되었습니다.",
			GroupType.BULK,
			ChannelType.EMAIL,
			2,
			0,
			0,
			2,
			false,
			null,
			List.of(
				new NotificationItemResult(101L, "user1@example.com", NotificationStatus.PENDING, null, null, null, true, java.time.LocalDateTime.of(2026, 3, 8, 12, 0)),
				new NotificationItemResult(102L, "user2@example.com", NotificationStatus.PENDING, null, null, null, false, null)
			)
		);

		// when
		NotificationGroupDetailResponse response = NotificationGroupDetailResponse.from(group);

		// then
		assertThat(response.title()).isEqualTo("주문 완료");
		assertThat(response.totalCount()).isEqualTo(2);
		assertThat(response.notifications()).hasSize(2);
		assertThat(response.notifications().get(0).receiver()).isEqualTo("user1@example.com");
		assertThat(response.notifications().get(1).receiver()).isEqualTo("user2@example.com");
		assertThat(response.notifications().get(0).isRead()).isTrue();
		assertThat(response.notifications().get(0).readAt()).isEqualTo(java.time.LocalDateTime.of(2026, 3, 8, 12, 0));
	}
}
