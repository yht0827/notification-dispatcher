package com.example.infrastructure.repository;

import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

	@Override
	@Query("select n from Notification n left join fetch n.group where n.id = :id")
	Optional<Notification> findById(@Param("id") Long id);

	@Query("select distinct n from Notification n left join fetch n.group where n.id in :ids")
	List<Notification> findAllByIdIn(@Param("ids") List<Long> ids);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select n from Notification n where n.id = :id")
	Optional<Notification> findByIdWithPessimisticLock(@Param("id") Long id);

    List<Notification> findByStatus(NotificationStatus status);

    List<Notification> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
        NotificationStatus status,
        LocalDateTime threshold,
        Pageable pageable
    );
}
