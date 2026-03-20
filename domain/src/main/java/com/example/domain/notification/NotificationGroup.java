package com.example.domain.notification;

import java.util.ArrayList;
import java.util.List;

import com.example.domain.common.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "notification_group",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "idx_notification_group_client_idempotency_key",
			columnNames = {"client_id", "idempotency_key"}
		)
	}
)
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

	@Embedded
	private NotificationStats stats = new NotificationStats();

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
		this.stats = new NotificationStats();
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
		this.stats.incrementTotal();
		return notification;
	}

	public void initializeTotalCount(int totalCount) {
		this.stats.initializeTotalCount(totalCount);
	}

	public void incrementSentCount() {
		this.stats.incrementSent();
	}

	public void incrementFailedCount() {
		this.stats.incrementFailed();
	}

	private static GroupType resolveGroupType(int receiverCount) {
		return receiverCount == 1 ? GroupType.SINGLE : GroupType.BULK;
	}
}
