package com.example.infrastructure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.domain.notification.NotificationReadStatus;

public interface NotificationReadStatusJpaRepository extends JpaRepository<NotificationReadStatus, Long> {

	List<NotificationReadStatus> findAllByNotificationIdIn(List<Long> notificationIds);
}
