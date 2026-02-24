package com.example.domain.outbox;

import java.time.LocalDateTime;

import com.example.domain.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Convert(converter = OutboxAggregateTypeConverter.class)
	@Column(nullable = false)
	private OutboxAggregateType aggregateType;

	@Column(nullable = false)
	private Long aggregateId;

	@Convert(converter = OutboxEventTypeConverter.class)
	@Column(nullable = false)
	private OutboxEventType eventType;

	@Column(columnDefinition = "TEXT")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OutboxStatus status;

	private LocalDateTime processedAt;

	private Outbox(OutboxAggregateType aggregateType, Long aggregateId, OutboxEventType eventType, String payload) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
	}

	public static Outbox create(OutboxAggregateType aggregateType, Long aggregateId, OutboxEventType eventType,
		String payload) {
		return new Outbox(aggregateType, aggregateId, eventType, payload);
	}

	public static Outbox createNotificationEvent(Long notificationId) {
		return new Outbox(OutboxAggregateType.NOTIFICATION, notificationId, OutboxEventType.NOTIFICATION_CREATED,
			null);
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
