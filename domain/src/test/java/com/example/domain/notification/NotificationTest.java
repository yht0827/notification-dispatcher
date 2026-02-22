package com.example.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import com.example.domain.exception.InvalidStatusTransitionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    @Test
    @DisplayName("알림 생성 시 초기 상태는 PENDING이다")
    void createNotification_initialStatusIsPending() {
        // given
        NotificationGroup group = createGroup();

        // when
        Notification notification = Notification.create(group, "user@example.com", "idem-key-123");

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getReceiver()).isEqualTo("user@example.com");
        assertThat(notification.getIdempotencyKey()).isEqualTo("idem-key-123");
        assertThat(notification.getAttemptCount()).isZero();
        assertThat(notification.getSentAt()).isNull();
    }

    @Test
    @DisplayName("발송 시작하면 상태가 SENDING으로 변경되고 시도 횟수가 증가한다")
    void startSending_statusChangesToSendingAndAttemptIncremented() {
        // given
        Notification notification = createNotification();

        // when
        notification.startSending();

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENDING);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("알림을 발송 완료 처리하면 상태가 SENT로 변경된다")
    void markAsSent_statusChangesToSent() {
        // given
        Notification notification = createNotification();
        notification.startSending();

        // when
        notification.markAsSent();

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("알림을 재시도 대기 처리하면 상태가 RETRY_WAIT로 변경된다")
    void markAsRetryWait_statusChangesToRetryWait() {
        // given
        Notification notification = createNotification();
        notification.startSending();
        LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(5);

        // when
        notification.markAsRetryWait(nextRetry);

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRY_WAIT);
        assertThat(notification.getNextRetryAt()).isEqualTo(nextRetry);
    }

    @Test
    @DisplayName("알림을 발송 실패 처리하면 상태가 FAILED로 변경된다")
    void markAsFailed_statusChangesToFailed() {
        // given
        Notification notification = createNotification();
        notification.startSending();

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
        Notification notification = createNotification();

        // when
        notification.cancel();

        // then
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.CANCELED);
    }

    @Test
    @DisplayName("이미 발송된 알림은 취소할 수 없다")
    void cancel_throwsExceptionWhenAlreadySent() {
        // given
        Notification notification = createNotification();
        notification.startSending();
        notification.markAsSent();

        // when & then
        assertThatThrownBy(notification::cancel)
            .isInstanceOf(InvalidStatusTransitionException.class)
            .hasMessageContaining("SENT에서 CANCELED로 변경할 수 없습니다");
    }

    @Test
    @DisplayName("PENDING 상태에서 SENT로 직접 전이할 수 없다")
    void markAsSent_throwsExceptionFromPendingStatus() {
        // given
        Notification notification = createNotification();

        // when & then
        assertThatThrownBy(notification::markAsSent)
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("재시도 가능 여부를 확인할 수 있다")
    void canRetry_returnsTrueWhenRetryWaitAndUnderMaxAttempts() {
        // given
        Notification notification = createNotification();
        notification.startSending();
        notification.markAsRetryWait(LocalDateTime.now().plusMinutes(5));

        // when & then
        assertThat(notification.canRetry(3)).isTrue();
    }

    @Test
    @DisplayName("최대 시도 횟수 초과 시 재시도 불가능하다")
    void canRetry_returnsFalseWhenOverMaxAttempts() {
        // given
        Notification notification = createNotification();
        notification.startSending();
        notification.markAsRetryWait(LocalDateTime.now().plusMinutes(5));
        notification.startSending();
        notification.markAsRetryWait(LocalDateTime.now().plusMinutes(5));
        notification.startSending();
        notification.markAsRetryWait(LocalDateTime.now().plusMinutes(5));

        // when & then
        assertThat(notification.canRetry(3)).isFalse();
    }

    @Test
    @DisplayName("알림은 그룹 정보에 접근할 수 있다")
    void notification_canAccessGroupInfo() {
        // given
        Notification notification = createNotification();

        // then
        NotificationGroup group = notification.getGroup();
        assertThat(group.getSender()).isEqualTo("MyShop");
        assertThat(group.getTitle()).isEqualTo("테스트 제목");
        assertThat(group.getContent()).isEqualTo("테스트 내용");
        assertThat(group.getChannelType()).isEqualTo(ChannelType.EMAIL);
        assertThat(group.getClientId()).isEqualTo("test-service");
    }

    @Test
    @DisplayName("종료 상태 여부를 확인할 수 있다")
    void isTerminal_returnsTrueForTerminalStatuses() {
        // given
        Notification notification = createNotification();
        notification.startSending();
        notification.markAsSent();

        // then
        assertThat(notification.isTerminal()).isTrue();
    }

    private NotificationGroup createGroup() {
        return NotificationGroup.create("test-service", "MyShop", "테스트 제목", "테스트 내용", ChannelType.EMAIL, 1);
    }

    private Notification createNotification() {
        return Notification.create(createGroup(), "user@example.com", "idem-key-123");
    }
}
