package com.example.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationGroupTest {

    @Test
    @DisplayName("그룹 생성 시 카운트는 모두 0이다")
    void createGroup_initialCountsAreZero() {
        // when
        NotificationGroup group = NotificationGroup.create(
                "test-service", "MyShop", "테스트 제목", "테스트 내용", ChannelType.EMAIL, 3);

        // then
        assertThat(group.getTotalCount()).isZero();
        assertThat(group.getSentCount()).isZero();
        assertThat(group.getFailedCount()).isZero();
        assertThat(group.getNotifications()).isEmpty();
    }

    @Test
    @DisplayName("그룹에 알림을 추가하면 totalCount가 증가한다")
    void addNotification_incrementsTotalCount() {
        // given
        NotificationGroup group = createBulkGroup();

        // when
        group.addNotification("user1@example.com", "idem-key-1");
        group.addNotification("user2@example.com", "idem-key-2");
        group.addNotification("user3@example.com", "idem-key-3");

        // then
        assertThat(group.getTotalCount()).isEqualTo(3);
        assertThat(group.getNotifications()).hasSize(3);
    }

    @Test
    @DisplayName("알림이 발송되면 sentCount가 증가한다")
    void markAsSent_incrementsSentCount() {
        // given
        NotificationGroup group = createBulkGroup();
        Notification notification = group.addNotification("user@example.com", "idem-key-1");

        // when
        notification.startSending();
        notification.markAsSent();

        // then
        assertThat(group.getSentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("알림이 실패하면 failedCount가 증가한다")
    void markAsFailed_incrementsFailedCount() {
        // given
        NotificationGroup group = createBulkGroup();
        Notification notification = group.addNotification("user@example.com", "idem-key-1");

        // when
        notification.startSending();
        notification.markAsFailed("전송 실패");

        // then
        assertThat(group.getFailedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("대기 중인 알림 수를 계산한다")
    void getPendingCount() {
        // given
        NotificationGroup group = createBulkGroup();
        Notification n1 = group.addNotification("user1@example.com", "idem-key-1");
        Notification n2 = group.addNotification("user2@example.com", "idem-key-2");
        group.addNotification("user3@example.com", "idem-key-3");

        n1.startSending();
        n1.markAsSent();
        n2.startSending();
        n2.markAsFailed("실패");

        // then
        assertThat(group.getPendingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("모든 알림이 처리되면 isCompleted가 true를 반환한다")
    void isCompleted_true() {
        // given
        NotificationGroup group = createBulkGroup();
        Notification n1 = group.addNotification("user1@example.com", "idem-key-1");
        Notification n2 = group.addNotification("user2@example.com", "idem-key-2");

        // when
        n1.startSending();
        n1.markAsSent();
        n2.startSending();
        n2.markAsFailed("실패");

        // then
        assertThat(group.isCompleted()).isTrue();
    }

    private NotificationGroup createBulkGroup() {
        return NotificationGroup.create(
                "test-service", "MyShop", "대량 발송 테스트", "테스트 내용입니다.", ChannelType.EMAIL, 3);
    }
}
