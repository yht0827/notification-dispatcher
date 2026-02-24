package com.example.infrastructure.repository;

import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

	@Override
	@Query("select n from Notification n left join fetch n.group where n.id = :id")
	Optional<Notification> findById(@Param("id") Long id);

	@Query("select n from Notification n left join fetch n.group where n.receiver = :receiver")
	List<Notification> findByReceiver(@Param("receiver") String receiver);

    List<Notification> findByReceiverAndStatus(String receiver, NotificationStatus status);

    List<Notification> findByStatus(NotificationStatus status);
}
