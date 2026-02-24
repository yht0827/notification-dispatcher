package com.example.api.dto.response;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.application.port.in.NotificationGroupSlice;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationGroup;

class NotificationBundleResponseTest {

	@Test
	@DisplayName("알림 묶음 목록 응답은 대표 1건을 제외한 moreCount를 계산한다")
	void listResponseMoreCount() {
		// given
		NotificationGroup group = NotificationGroup.create(
			"news-service",
			"NewsBot",
			"속보",
			"시장 마감 브리핑",
			ChannelType.EMAIL,
			3
		);
		group.addNotification("a@example.com");
		group.addNotification("b@example.com");
		group.addNotification("c@example.com");

		// when
		NotificationListResponse response = NotificationListResponse.from(group);

		// then
		assertThat(response.title()).isEqualTo("속보");
		assertThat(response.content()).isEqualTo("시장 마감 브리핑");
		assertThat(response.totalCount()).isEqualTo(3);
		assertThat(response.moreCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("알림 그룹 상세 응답은 그룹 내 개별 알림 목록을 포함한다")
	void detailResponseIncludesNotifications() {
		// given
		NotificationGroup group = NotificationGroup.create(
			"order-service",
			"MyShop",
			"주문 완료",
			"주문이 정상 처리되었습니다.",
			ChannelType.EMAIL,
			2
		);
		group.addNotification("user1@example.com");
		group.addNotification("user2@example.com");

		// when
		NotificationGroupDetailResponse response = NotificationGroupDetailResponse.from(group);

		// then
		assertThat(response.title()).isEqualTo("주문 완료");
		assertThat(response.totalCount()).isEqualTo(2);
		assertThat(response.notifications()).hasSize(2);
		assertThat(response.notifications().get(0).receiver()).isEqualTo("user1@example.com");
		assertThat(response.notifications().get(1).receiver()).isEqualTo("user2@example.com");
	}

	@Test
	@DisplayName("알림 묶음 커서 응답은 아이템과 커서 정보를 포함한다")
	void listSliceResponseIncludesCursorMetadata() {
		// given
		NotificationGroup group = NotificationGroup.create(
			"news-service",
			"NewsBot",
			"속보",
			"시장 마감 브리핑",
			ChannelType.EMAIL,
			1
		);

		NotificationGroupSlice slice = new NotificationGroupSlice(
			java.util.List.of(group),
			true,
			99L
		);

		// when
		NotificationListSliceResponse response = NotificationListSliceResponse.from(slice);

		// then
		assertThat(response.items()).hasSize(1);
		assertThat(response.items().getFirst().title()).isEqualTo("속보");
		assertThat(response.hasNext()).isTrue();
		assertThat(response.nextCursorId()).isEqualTo(99L);
	}
}
