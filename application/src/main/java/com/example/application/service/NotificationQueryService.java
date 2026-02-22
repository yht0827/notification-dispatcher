package com.example.application.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
