package com.example.infrastructure.repository;

import com.example.application.port.out.NotificationGroupRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationGroup;
import com.example.infrastructure.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationGroupRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private NotificationGroupRepository groupRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("그룹을 저장하고 조회한다")
    void saveAndFindById() {
        // given
        NotificationGroup group = NotificationGroup.create(
                "test-service", "MyShop", "테스트 제목", "테스트 내용", ChannelType.EMAIL, 1);

        // when
        NotificationGroup saved = groupRepository.save(group);
        NotificationGroup found = groupRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found.getTitle()).isEqualTo("테스트 제목");
        assertThat(found.getClientId()).isEqualTo("test-service");
    }

    @Test
    @DisplayName("clientId와 날짜 범위로 그룹을 조회한다")
    void findByClientIdWithCursor_returnsMatchingGroups() {
        // given
        groupRepository.save(createSingleGroup("service-a"));
        groupRepository.save(createBulkGroup("service-a"));
        groupRepository.save(createSingleGroup("service-b"));

        // when
        java.time.LocalDateTime from = java.time.LocalDateTime.now().minusDays(7);
        List<NotificationGroup> groups = groupRepository.findByClientIdWithCursor("service-a", from, null, 10);

        // then
        assertThat(groups).hasSize(2);
        assertThat(groups).allMatch(g -> g.getClientId().equals("service-a"));
    }

    @Test
    @DisplayName("groupType으로 그룹을 조회한다")
    void findByGroupType() {
        // given
        groupRepository.save(createSingleGroup("service-a"));
        groupRepository.save(createBulkGroup("service-b"));
        groupRepository.save(createBulkGroup("service-c"));

        // when
        List<NotificationGroup> bulkGroups = groupRepository.findByGroupType(GroupType.BULK);

        // then
        assertThat(bulkGroups).hasSize(2);
    }

    @Test
    @DisplayName("clientId와 idempotencyKey로 기존 그룹을 조회한다")
    void findByClientIdAndIdempotencyKey() {
        // given
        NotificationGroup group = NotificationGroup.create(
                "service-a",
                "idem-order-1001",
                "MyShop",
                "테스트",
                "테스트 내용",
                ChannelType.EMAIL,
                1
        );
        NotificationGroup saved = groupRepository.save(group);

        // when
        NotificationGroup found = groupRepository
                .findByClientIdAndIdempotencyKey("service-a", "idem-order-1001")
                .orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getClientId()).isEqualTo("service-a");
        assertThat(found.getIdempotencyKey()).isEqualTo("idem-order-1001");
    }

    @Test
    @DisplayName("그룹에 알림을 추가하면 함께 저장된다")
    void saveGroupWithNotifications() {
        // given
        NotificationGroup group = createBulkGroup("test-service");
        group.addNotification("user1@example.com");
        group.addNotification("user2@example.com");

        // when
        NotificationGroup saved = groupRepository.save(group);

        // then
        assertThat(saved.getTotalCount()).isEqualTo(2);
        assertThat(saved.getNotifications()).hasSize(2);
    }

    @Test
    @DisplayName("그룹 상세 조회 시 알림 목록을 함께 가져온다")
    void findByIdWithNotifications() {
        // given
        NotificationGroup group = createBulkGroup("detail-service");
        group.addNotification("detail-user1@example.com");
        group.addNotification("detail-user2@example.com");
        NotificationGroup saved = groupRepository.save(group);

        // when
        NotificationGroup found = groupRepository.findByIdWithNotifications(saved.getId()).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getNotifications()).hasSize(2);
    }

	@Test
	@DisplayName("커서 없이 최근 그룹을 최신순으로 조회한다")
	void findRecentByCursor_withoutCursor() throws InterruptedException {
		// given
		NotificationGroup first = groupRepository.save(createSingleGroup("recent-a"));
		Thread.sleep(5);
		NotificationGroup second = groupRepository.save(createSingleGroup("recent-b"));
		Thread.sleep(5);
		NotificationGroup third = groupRepository.save(createSingleGroup("recent-c"));

		// when
		List<NotificationGroup> groups = groupRepository.findRecentByCursor(null, 2);

		// then
		assertThat(groups).hasSize(2);
		assertThat(groups.get(0).getId()).isEqualTo(third.getId());
		assertThat(groups.get(1).getId()).isEqualTo(second.getId());
		assertThat(groups.get(0).getId()).isNotEqualTo(first.getId());
		assertThat(groups.get(1).getId()).isNotEqualTo(first.getId());
	}

	@Test
	@DisplayName("커서를 전달하면 다음 그룹 목록을 조회한다")
	void findRecentByCursor_withCursor() throws InterruptedException {
		// given
		NotificationGroup first = groupRepository.save(createSingleGroup("cursor-a"));
		Thread.sleep(5);
		NotificationGroup second = groupRepository.save(createSingleGroup("cursor-b"));
		Thread.sleep(5);
		groupRepository.save(createSingleGroup("cursor-c"));

		// when
		List<NotificationGroup> firstSlice = groupRepository.findRecentByCursor(null, 2);
		Long cursorId = firstSlice.get(1).getId();
		List<NotificationGroup> secondSlice = groupRepository.findRecentByCursor(cursorId, 2);

		// then
		assertThat(firstSlice).hasSize(2);
		assertThat(secondSlice).hasSize(1);
		assertThat(secondSlice.getFirst().getId()).isEqualTo(first.getId());
		assertThat(secondSlice.getFirst().getId()).isNotEqualTo(second.getId());
	}

    @Test
    @DisplayName("7일 이전에 생성된 그룹은 조회되지 않는다")
    void findByClientIdWithCursor_excludesOldGroups() {
        // given
        NotificationGroup recent = groupRepository.save(createSingleGroup("old-test"));
        NotificationGroup old = groupRepository.save(createSingleGroup("old-test"));

        // 8일 전으로 backdating
        jdbcTemplate.update(
            "UPDATE notification_group SET created_at = ? WHERE id = ?",
            LocalDateTime.now().minusDays(8), old.getId()
        );

        // when
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        List<NotificationGroup> groups = groupRepository.findByClientIdWithCursor("old-test", from, null, 10);

        // then
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getId()).isEqualTo(recent.getId());
    }

    @Test
    @DisplayName("cursorId보다 작은 id의 그룹만 조회한다")
    void findByClientIdWithCursor_withCursor() {
        // given
        NotificationGroup first = groupRepository.save(createSingleGroup("cursor-test"));
        groupRepository.save(createSingleGroup("cursor-test"));
        groupRepository.save(createSingleGroup("cursor-test"));

        // when
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        List<NotificationGroup> firstPage = groupRepository.findByClientIdWithCursor("cursor-test", from, null, 2);
        Long cursorId = firstPage.get(1).getId();
        List<NotificationGroup> secondPage = groupRepository.findByClientIdWithCursor("cursor-test", from, cursorId, 2);

        // then
        assertThat(firstPage).hasSize(2);
        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.getFirst().getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("다른 clientId의 그룹은 조회되지 않는다")
    void findByClientIdWithCursor_excludesOtherClients() {
        // given
        groupRepository.save(createSingleGroup("my-service"));
        groupRepository.save(createSingleGroup("other-service"));

        // when
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        List<NotificationGroup> groups = groupRepository.findByClientIdWithCursor("my-service", from, null, 10);

        // then
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getClientId()).isEqualTo("my-service");
    }

    private NotificationGroup createSingleGroup(String clientId) {
        return NotificationGroup.create(clientId, "MyShop", "테스트", "테스트 내용", ChannelType.EMAIL, 1);
    }

    private NotificationGroup createBulkGroup(String clientId) {
        return NotificationGroup.create(clientId, "MyShop", "테스트", "테스트 내용", ChannelType.EMAIL, 3);
    }
}
