package com.example.domain.notification;

import java.util.ArrayList;
import java.util.List;

import com.example.domain.common.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationGroup extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String clientId;

	@Column(name = "idempotency_key")
	private String idempotencyKey;

	@Column(nullable = false)
	private String sender;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GroupType groupType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChannelType channelType;

	private int totalCount;

	private int sentCount;

	private int failedCount;

	@OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Notification> notifications = new ArrayList<>();

	private NotificationGroup(
		String clientId,
		String idempotencyKey,
		String sender,
		String title,
		String content,
		GroupType groupType,
		ChannelType channelType
	) {
		this.clientId = clientId;
		this.idempotencyKey = idempotencyKey;
		this.sender = sender;
		this.title = title;
		this.content = content;
		this.groupType = groupType;
		this.channelType = channelType;
		this.totalCount = 0;
		this.sentCount = 0;
		this.failedCount = 0;
	}

	public static NotificationGroup create(String clientId, String sender, String title,
		String content, ChannelType channelType, int receiverCount) {
		return create(clientId, null, sender, title, content, channelType, receiverCount);
	}

	public static NotificationGroup create(
		String clientId,
		String idempotencyKey,
		String sender,
		String title,
		String content,
		ChannelType channelType,
		int receiverCount
	) {
		GroupType groupType = resolveGroupType(receiverCount);
		return new NotificationGroup(clientId, idempotencyKey, sender, title, content, groupType, channelType);
	}

	public Notification addNotification(String receiver) {
		Notification notification = Notification.create(this, receiver);
		this.notifications.add(notification);
		this.totalCount++;
		return notification;
	}

	public void incrementSentCount() {
		this.sentCount++;
	}

	public void incrementFailedCount() {
		this.failedCount++;
	}

	public int getPendingCount() {
		return totalCount - processedCount();
	}

	public boolean isCompleted() {
		return totalCount == processedCount();
	}

	private static GroupType resolveGroupType(int receiverCount) {
		return receiverCount == 1 ? GroupType.SINGLE : GroupType.BULK;
	}

	private int processedCount() {
		return sentCount + failedCount;
	}
}
