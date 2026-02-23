package com.example.application.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationGroupSlice;
import com.example.application.port.in.NotificationQueryUseCase;
import com.example.application.port.out.NotificationGroupRepository;
import com.example.application.port.out.NotificationRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService implements NotificationQueryUseCase {

	private final NotificationGroupRepository groupRepository;
	private final NotificationRepository notificationRepository;

	@Override
	public Optional<NotificationGroup> getGroup(Long groupId) {
		return groupRepository.findById(groupId);
	}

	@Override
	public Optional<NotificationGroup> getGroupDetail(Long groupId) {
		return groupRepository.findByIdWithNotifications(groupId);
	}

	@Override
	public NotificationGroupSlice getRecentGroups(Long cursorId, int size) {
		int normalizedSize = Math.max(size, 1);
		List<NotificationGroup> fetched = groupRepository.findRecentByCursor(cursorId, normalizedSize + 1);

		boolean hasNext = fetched.size() > normalizedSize;
		List<NotificationGroup> items = hasNext
			? List.copyOf(fetched.subList(0, normalizedSize))
			: List.copyOf(fetched);
		Long nextCursorId = hasNext && !items.isEmpty()
			? items.get(items.size() - 1).getId()
			: null;

		return new NotificationGroupSlice(items, hasNext, nextCursorId);
	}

	@Override
	public List<NotificationGroup> getGroupsByClientId(String clientId) {
		return groupRepository.findByClientId(clientId);
	}

	@Override
	public Optional<Notification> getNotification(Long notificationId) {
		return notificationRepository.findById(notificationId);
	}

	@Override
	public List<Notification> getNotificationsByReceiver(String receiver) {
		return notificationRepository.findByReceiver(receiver);
	}
}
