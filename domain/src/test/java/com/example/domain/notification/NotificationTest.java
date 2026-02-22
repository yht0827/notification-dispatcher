package com.example.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    @Test
    @DisplayName("알림 생성 시 초기 상태는 PENDING이다")
    void createNotification_initialStatusIsPending() {
        // given
        NotificationGroup group = createGroup();

        // when
        Notification notification = Notification.create(group, "user@example.com");

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getReceiver()).isEqualTo("user@example.com");
        assertThat(notification.getSentAt()).isNull();
    }

    @Test
    @DisplayName("알림을 발송 완료 처리하면 상태가 SENT로 변경된다")
    void markAsSent_statusChangesToSent() {
        // given
        NotificationGroup group = createGroup();
        Notification notification = Notification.create(group, "user@example.com");

        // when
        notification.markAsSent();

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("알림을 발송 실패 처리하면 상태가 FAILED로 변경된다")
    void markAsFailed_statusChangesToFailed() {
        // given
        NotificationGroup group = createGroup();
        Notification notification = Notification.create(group, "user@example.com");

        // when
        notification.markAsFailed("발송 실패");

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailReason()).isEqualTo("발송 실패");
    }

    @Test
    @DisplayName("알림을 취소하면 상태가 CANCELED로 변경된다")
    void cancel_statusChangesToCanceled() {
        // given
        NotificationGroup group = createGroup();
        Notification notification = Notification.create(group, "user@example.com");

        // when
        notification.cancel();

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.CANCELED);
    }

    @Test
    @DisplayName("알림은 그룹의 정보를 위임받아 반환한다")
    void notification_delegatesToGroup() {
        // given
        NotificationGroup group = createGroup();
        Notification notification = Notification.create(group, "user@example.com");

        // then
        assertThat(notification.getSender()).isEqualTo("MyShop");
        assertThat(notification.getTitle()).isEqualTo("테스트 제목");
        assertThat(notification.getContent()).isEqualTo("테스트 내용");
        assertThat(notification.getChannelType()).isEqualTo(ChannelType.EMAIL);
        assertThat(notification.getClientId()).isEqualTo("test-service");
    }

    private NotificationGroup createGroup() {
        return NotificationGroup.createSingle("test-service", "MyShop", "테스트 제목", "테스트 내용", ChannelType.EMAIL);
    }
}
