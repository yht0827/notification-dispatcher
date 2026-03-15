package com.example.application.port.in;

import java.util.Optional;

import com.example.application.port.in.result.CursorSlice;
import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationResult;
import com.example.application.port.in.result.NotificationUnreadCountResult;

public interface NotificationQueryUseCase {

	Optional<NotificationGroupResult> getGroup(Long groupId);

	Optional<NotificationGroupDetailResult> getGroupDetail(Long groupId);

	CursorSlice<NotificationGroupResult> getGroupsByClientId(String clientId, Long cursorId, int size, Boolean completed);

	CursorSlice<NotificationResult> getNotificationsByReceiver(String clientId, String receiver, Long cursorId, int size);

	Optional<NotificationResult> getNotification(Long notificationId);

	NotificationUnreadCountResult getUnreadCount(String clientId, String receiver);
}
