package com.example.domain.notification;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_read_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationReadStatus {

	@Id
	@Column(name = "notification_id", nullable = false)
	private Long notificationId;

	@Column(name = "read_at", nullable = false)
	private LocalDateTime readAt;

	private NotificationReadStatus(Long notificationId, LocalDateTime readAt) {
		this.notificationId = notificationId;
		this.readAt = readAt;
	}

	public static NotificationReadStatus create(Long notificationId, LocalDateTime readAt) {
		return new NotificationReadStatus(notificationId, readAt);
	}
}
