package com.example.domain.outbox;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String aggregateType;

	@Column(nullable = false)
	private Long aggregateId;

	@Column(nullable = false)
	private String eventType;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OutboxStatus status;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime processedAt;

	private OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
		this.createdAt = LocalDateTime.now();
	}

	public static OutboxEvent create(String aggregateType, Long aggregateId, String eventType, String payload) {
		return new OutboxEvent(aggregateType, aggregateId, eventType, payload);
	}

	public void markAsProcessed() {
		this.status = OutboxStatus.PROCESSED;
		this.processedAt = LocalDateTime.now();
	}

	public void markAsFailed() {
		this.status = OutboxStatus.FAILED;
	}
}
