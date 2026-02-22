package com.example.infrastructure.repository;

import com.example.application.port.out.NotificationGroupRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationGroup;
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
class NotificationGroupRepositoryTest {

    @Autowired
    private NotificationGroupRepository groupRepository;

    @Test
    @DisplayName("그룹을 저장하고 조회한다")
    void saveAndFindById() {
        // given
        NotificationGroup group = NotificationGroup.createSingle(
                "test-service", "MyShop", "테스트 제목", "테스트 내용", ChannelType.EMAIL);

        // when
        NotificationGroup saved = groupRepository.save(group);
        NotificationGroup found = groupRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found.getTitle()).isEqualTo("테스트 제목");
        assertThat(found.getClientId()).isEqualTo("test-service");
    }

    @Test
    @DisplayName("clientId로 그룹을 조회한다")
    void findByClientId() {
        // given
        groupRepository.save(createSingleGroup("service-a"));
        groupRepository.save(createBulkGroup("service-a"));
        groupRepository.save(createSingleGroup("service-b"));

        // when
        List<NotificationGroup> groups = groupRepository.findByClientId("service-a");

        // then
        assertThat(groups).hasSize(2);
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

    private NotificationGroup createSingleGroup(String clientId) {
        return NotificationGroup.createSingle(clientId, "MyShop", "테스트", "테스트 내용", ChannelType.EMAIL);
    }

    private NotificationGroup createBulkGroup(String clientId) {
        return NotificationGroup.createBulk(clientId, "MyShop", "테스트", "테스트 내용", ChannelType.EMAIL);
    }
}
