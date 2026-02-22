package com.example.domain.notification;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.example.domain.common.BaseEntity;

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

	private String failReason;

	private Notification(NotificationGroup group, String receiver) {
		this.group = group;
		this.receiver = receiver;
		this.status = NotificationStatus.PENDING;
	}

	public static Notification create(NotificationGroup group, String receiver) {
		return new Notification(group, receiver);
	}

	public String getSender() {
		return group != null ? group.getSender() : null;
	}

	public String getClientId() {
		return group != null ? group.getClientId() : null;
	}

	public String getTitle() {
		return group != null ? group.getTitle() : null;
	}

	public String getContent() {
		return group != null ? group.getContent() : null;
	}

	public ChannelType getChannelType() {
		return group != null ? group.getChannelType() : null;
	}

	public void markAsSent() {
		this.status = NotificationStatus.SENT;
		this.sentAt = LocalDateTime.now();
		if (group != null) {
			group.incrementSentCount();
		}
	}

	public void markAsFailed(String reason) {
		this.status = NotificationStatus.FAILED;
		this.failReason = reason;
		if (group != null) {
			group.incrementFailedCount();
		}
	}

	public void cancel() {
		this.status = NotificationStatus.CANCELED;
	}
}
