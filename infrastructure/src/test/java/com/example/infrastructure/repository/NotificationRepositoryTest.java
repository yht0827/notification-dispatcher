package com.example.infrastructure.repository;

import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.notification.*;
import com.example.infrastructure.support.IntegrationTestSupport;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationGroupRepository groupRepository;

	@Autowired
	private NotificationReadStatusJpaRepository notificationReadStatusJpaRepository;

    @Test
    @DisplayName("알림을 저장하고 조회한다")
    void saveAndFindById() {
        // given
        NotificationGroup group = createAndSaveGroup();
        Notification notification = group.addNotification("user@example.com");
        groupRepository.save(group);

        // when
        Notification found = notificationRepository.findById(notification.getId()).orElseThrow();

        // then
        assertThat(found.getReceiver()).isEqualTo("user@example.com");
        assertThat(found.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(Hibernate.isInitialized(found.getGroup())).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("findById 조회 결과는 트랜잭션 밖 DTO 매핑에서도 group 필드를 읽을 수 있다")
    void findById_allowsGroupFieldAccessOutsideTransaction() {
        // given
        NotificationGroup group = createAndSaveGroup();
        Notification notification = group.addNotification("outside-tx@example.com");
        NotificationGroup saved = groupRepository.save(group);

        // when
        Notification found = notificationRepository.findById(notification.getId()).orElseThrow();

        // then
        assertThat(found.getGroup().getSender()).isEqualTo(saved.getSender());
        assertThat(found.getGroup().getTitle()).isEqualTo(saved.getTitle());
    }

    @Test
    @DisplayName("상태로 알림을 조회한다")
    void findByStatus() {
        // given
        NotificationGroup group = createAndSaveGroup();
        Notification n1 = group.addNotification("user1@example.com");
        Notification n2 = group.addNotification("user2@example.com");
        group.addNotification("user3@example.com");
        groupRepository.save(group);

        n1.startSending();
        n1.markAsSent();
        n2.startSending();
        n2.markAsFailed("실패");
        groupRepository.save(group);

        // when
        List<Notification> pending = notificationRepository.findByStatus(NotificationStatus.PENDING);

        // then
        assertThat(pending).hasSize(1);
    }

	@Test
	@DisplayName("clientId + receiver + 최근 7일 기준으로 읽지 않은 알림 개수를 센다")
	void countUnreadByClientIdAndReceiver() {
		NotificationGroup group = NotificationGroup.create(
			"client-a", "MyShop", "테스트", "내용", ChannelType.EMAIL, 3
		);
		Notification unreadRecent = group.addNotification("user@example.com");
		Notification readRecent = group.addNotification("user@example.com");
		group.addNotification("other@example.com");
		groupRepository.save(group);

		NotificationReadStatus readStatus = NotificationReadStatus.create(readRecent.getId(), LocalDateTime.now());
		notificationReadStatusJpaRepository.save(readStatus);

		long count = notificationRepository.countUnreadByClientIdAndReceiver(
			"client-a",
			"user@example.com",
			LocalDateTime.now().minusDays(7)
		);

		assertThat(count).isEqualTo(1L);
	}

    private NotificationGroup createAndSaveGroup() {
        return NotificationGroup.create("test-service", "MyShop", "테스트", "테스트 내용", ChannelType.EMAIL, 1);
    }
}
