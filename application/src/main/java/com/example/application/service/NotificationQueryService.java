package com.example.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.mapper.NotificationResultMapper;
import com.example.application.port.in.NotificationQueryUseCase;
import com.example.application.port.in.result.CursorSlice;
import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationListResult;
import com.example.application.port.in.result.NotificationResult;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.application.port.out.repository.NotificationRepository;

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
	public CursorSlice<NotificationListResult> getRecentGroups(Long cursorId, int size) {
		int limit = normalizeSize(size);
		List<NotificationListResult> fetched = groupRepository.findRecentByCursor(cursorId, limit + 1)
			.stream()
			.map(mapper::toListResult)
			.toList();
		return CursorSlice.of(fetched, limit, NotificationListResult::groupId);
	}

	@Override
	public CursorSlice<NotificationGroupResult> getGroupsByClientId(String clientId, Long cursorId,
		int size) {
		int limit = normalizeSize(size);
		LocalDateTime from = LocalDateTime.now().minusDays(7);
		List<NotificationGroupResult> fetched = groupRepository.findByClientIdWithCursor(clientId, from, cursorId,
				limit + 1)
			.stream()
			.map(mapper::toGroupResult)
			.toList();
		return CursorSlice.of(fetched, limit, NotificationGroupResult::id);
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

	private int normalizeSize(int size) {
		return Math.max(size, 1);
	}
}
