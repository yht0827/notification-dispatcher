package com.example.domain.outbox;

import java.time.LocalDateTime;

import com.example.domain.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "outbox", indexes = {
	@Index(name = "idx_outbox_status_created", columnList = "status, createdAt")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String aggregateType;

	@Column(nullable = false)
	private Long aggregateId;

	@Column(nullable = false)
	private String eventType;

	@Column(columnDefinition = "TEXT")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OutboxStatus status;

	private LocalDateTime processedAt;

	private Outbox(String aggregateType, Long aggregateId, String eventType, String payload) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
	}

	public static Outbox create(String aggregateType, Long aggregateId, String eventType, String payload) {
		return new Outbox(aggregateType, aggregateId, eventType, payload);
	}

	public static Outbox createNotificationEvent(Long notificationId) {
		return new Outbox("Notification", notificationId, "NotificationCreated", null);
	}

	public void markAsProcessed() {
		this.status = OutboxStatus.PROCESSED;
		this.processedAt = LocalDateTime.now();
	}

	public void markAsFailed() {
		this.status = OutboxStatus.FAILED;
	}

	public boolean isPending() {
		return this.status == OutboxStatus.PENDING;
	}
}
