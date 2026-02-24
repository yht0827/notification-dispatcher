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

	public void startSending() {
		transitionTo(NotificationStatus.SENDING);
		this.attemptCount++;
	}

	public void markAsSent() {
		transitionTo(NotificationStatus.SENT);
		this.sentAt = LocalDateTime.now();
		incrementSentCountIfGrouped();
	}

	public void markAsFailed(String reason) {
		transitionTo(NotificationStatus.FAILED);
		this.failReason = reason;
		incrementFailedCountIfGrouped();
	}

	public void cancel() {
		transitionTo(NotificationStatus.CANCELED);
	}

	public boolean isTerminal() {
		return this.status.isTerminal();
	}

	private void transitionTo(NotificationStatus targetStatus) {
		if (!this.status.canTransitionTo(targetStatus)) {
			throw new InvalidStatusTransitionException(this.status, targetStatus);
		}
		this.status = targetStatus;
	}

	private void incrementSentCountIfGrouped() {
		if (this.group != null) {
			this.group.incrementSentCount();
		}
	}

	private void incrementFailedCountIfGrouped() {
		if (this.group != null) {
			this.group.incrementFailedCount();
		}
	}
}
