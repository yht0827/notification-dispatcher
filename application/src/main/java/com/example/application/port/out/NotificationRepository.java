package com.example.application.port.out;

import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    List<Notification> findByReceiver(String receiver);

	List<Notification> findByReceiverAndStatus(String receiver, NotificationStatus status);

	List<Notification> findByStatus(NotificationStatus status);

	void delete(Notification notification);
}
