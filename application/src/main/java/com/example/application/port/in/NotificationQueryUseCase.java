package com.example.application.port.in;

import java.util.List;
import java.util.Optional;

import com.example.application.port.in.result.CursorSlice;
import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.in.result.NotificationListResult;
import com.example.application.port.in.result.NotificationResult;

public interface NotificationQueryUseCase {

	Optional<NotificationGroupResult> getGroup(Long groupId);

	Optional<NotificationGroupDetailResult> getGroupDetail(Long groupId);

	CursorSlice<NotificationListResult> getRecentGroups(Long cursorId, int size);

	CursorSlice<NotificationGroupResult> getGroupsByClientId(String clientId, Long cursorId, int size);

	Optional<NotificationResult> getNotification(Long notificationId);

	List<NotificationResult> getNotificationsByReceiver(String receiver);
}
