package com.example.infrastructure.repository;

import com.example.application.port.out.NotificationGroupRepository;
import com.example.application.port.out.NotificationRepository;
import com.example.domain.notification.*;
import com.example.infrastructure.TestApplication;
import com.example.infrastructure.config.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationGroupRepository groupRepository;

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
    }

    @Test
    @DisplayName("receiver로 알림을 조회한다")
    void findByReceiver() {
        // given
        NotificationGroup group = createAndSaveGroup();
        group.addNotification("user@example.com");
        group.addNotification("user@example.com");
        group.addNotification("other@example.com");
        groupRepository.save(group);

        // when
        List<Notification> notifications = notificationRepository.findByReceiver("user@example.com");

        // then
        assertThat(notifications).hasSize(2);
    }

    @Test
    @DisplayName("receiver와 상태로 알림을 조회한다")
    void findByReceiverAndStatus() {
        // given
        NotificationGroup group = createAndSaveGroup();
        Notification n1 = group.addNotification("user@example.com");
        group.addNotification("user@example.com");
        groupRepository.save(group);

        n1.markAsSent();
        groupRepository.save(group);

        // when
        List<Notification> sent = notificationRepository.findByReceiverAndStatus("user@example.com", NotificationStatus.SENT);
        List<Notification> pending = notificationRepository.findByReceiverAndStatus("user@example.com", NotificationStatus.PENDING);

        // then
        assertThat(sent).hasSize(1);
        assertThat(pending).hasSize(1);
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

        n1.markAsSent();
        n2.markAsFailed("실패");
        groupRepository.save(group);

        // when
        List<Notification> pending = notificationRepository.findByStatus(NotificationStatus.PENDING);

        // then
        assertThat(pending).hasSize(1);
    }

    private NotificationGroup createAndSaveGroup() {
        return NotificationGroup.createSingle("test-service", "MyShop", "테스트", "테스트 내용", ChannelType.EMAIL);
    }
}
