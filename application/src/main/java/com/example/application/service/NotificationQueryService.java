package com.example.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationGroupSlice;
import com.example.application.port.in.NotificationQueryUseCase;
import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationListResult;
import com.example.application.port.in.result.NotificationResult;
import com.example.application.port.out.NotificationGroupRepository;
import com.example.application.port.out.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService implements NotificationQueryUseCase {

	private final NotificationGroupRepository groupRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationResultMapper mapper;

	@Override
	public Optional<NotificationGroupResult> getGroup(Long groupId) {
		return groupRepository.findById(groupId).map(mapper::toGroupResult);
	}

	@Override
	public Optional<NotificationGroupDetailResult> getGroupDetail(Long groupId) {
		return groupRepository.findByIdWithNotifications(groupId).map(mapper::toGroupDetailResult);
	}

	@Override
	public NotificationGroupSlice<NotificationListResult> getRecentGroups(Long cursorId, int size) {
		int limit = Math.max(size, 1);
		List<NotificationListResult> fetched = groupRepository.findRecentByCursor(cursorId, limit + 1)
			.stream()
			.map(mapper::toListResult)
			.toList();
		return NotificationGroupSlice.of(fetched, limit, NotificationListResult::groupId);
	}

	@Override
	public NotificationGroupSlice<NotificationGroupResult> getGroupsByClientId(String clientId, Long cursorId,
		int size) {
		int limit = Math.max(size, 1);
		LocalDateTime from = LocalDateTime.now().minusDays(7);
		List<NotificationGroupResult> fetched = groupRepository.findByClientIdWithCursor(clientId, from, cursorId,
				limit + 1)
			.stream()
			.map(mapper::toGroupResult)
			.toList();
		return NotificationGroupSlice.of(fetched, limit, NotificationGroupResult::id);
	}

	@Override
	public Optional<NotificationResult> getNotification(Long notificationId) {
		return notificationRepository.findById(notificationId).map(mapper::toNotificationResult);
	}

	@Override
	public List<NotificationResult> getNotificationsByReceiver(String receiver) {
		return notificationRepository.findByReceiver(receiver)
			.stream()
			.map(mapper::toNotificationResult)
			.toList();
	}
}
