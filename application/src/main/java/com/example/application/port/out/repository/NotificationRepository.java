package com.example.application.port.out.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;

public interface NotificationRepository {

	Notification save(Notification notification);

	List<Notification> saveAll(List<Notification> notifications);

	List<Long> bulkInsertPending(Long groupId, List<String> receivers, LocalDateTime createdAt);

	Optional<Notification> findById(Long id);

	List<Notification> findAllByIdIn(List<Long> ids);

	long countUnreadByClientIdAndReceiver(String clientId, String receiver, LocalDateTime from);

	List<Notification> findByClientIdAndReceiverWithCursor(String clientId, String receiver, LocalDateTime from,
		Long cursorId, int limit);

	List<Notification> findByStatus(NotificationStatus status);

	List<Notification> findByStatusAndCreatedAtBefore(NotificationStatus status, LocalDateTime threshold, int limit);

	void delete(Notification notification);
}
