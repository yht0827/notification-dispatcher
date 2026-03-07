package com.example.application.port.out.repository;

import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    List<Notification> findByReceiver(String receiver);

	List<Notification> findByReceiverAndStatus(String receiver, NotificationStatus status);

	List<Notification> findByStatus(NotificationStatus status);

	List<Notification> findByStatusAndCreatedAtBefore(NotificationStatus status, LocalDateTime threshold, int limit);

	void delete(Notification notification);
}
