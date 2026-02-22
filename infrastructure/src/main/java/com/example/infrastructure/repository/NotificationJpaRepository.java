package com.example.infrastructure.repository;

import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByReceiver(String receiver);

    List<Notification> findByReceiverAndStatus(String receiver, NotificationStatus status);

    List<Notification> findByStatus(NotificationStatus status);
}
