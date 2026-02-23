package com.example.domain.notification;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.example.domain.common.BaseEntity;
import com.example.domain.exception.InvalidStatusTransitionException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE notification SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Notification extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id")
	private NotificationGroup group;

	@Column(nullable = false)
	private String receiver;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationStatus status;

	private LocalDateTime sentAt;

	private int attemptCount;

	private LocalDateTime nextRetryAt;

	private String failReason;

	private Notification(NotificationGroup group, String receiver) {
		this.group = group;
		this.receiver = receiver;
		this.status = NotificationStatus.PENDING;
		this.attemptCount = 0;
	}

	public static Notification create(NotificationGroup group, String receiver) {
		return new Notification(group, receiver);
	}

	// === 상태 전이 메서드 ===

	public void startSending() {
		transitionTo(NotificationStatus.SENDING);
		this.attemptCount++;
	}

	public void markAsSent() {
		transitionTo(NotificationStatus.SENT);
		this.sentAt = LocalDateTime.now();
		if (group != null) {
			group.incrementSentCount();
		}
	}

	public void markAsRetryWait(LocalDateTime nextRetryAt) {
		transitionTo(NotificationStatus.RETRY_WAIT);
		this.nextRetryAt = nextRetryAt;
	}

	public void markAsFailed(String reason) {
		transitionTo(NotificationStatus.FAILED);
		this.failReason = reason;
		if (group != null) {
			group.incrementFailedCount();
		}
	}

	public void cancel() {
		transitionTo(NotificationStatus.CANCELED);
	}

	// === 상태 조회 메서드 ===

	public boolean canRetry(int maxAttempts) {
		return this.attemptCount < maxAttempts && this.status.isRetryable();
	}

	public boolean isRetryDue() {
		return this.status == NotificationStatus.RETRY_WAIT
			&& this.nextRetryAt != null
			&& LocalDateTime.now().isAfter(this.nextRetryAt);
	}

	public boolean isTerminal() {
		return this.status.isTerminal();
	}

	// === Private ===

	private void transitionTo(NotificationStatus targetStatus) {
		if (!this.status.canTransitionTo(targetStatus)) {
			throw new InvalidStatusTransitionException(this.status, targetStatus);
		}
		this.status = targetStatus;
	}
}
