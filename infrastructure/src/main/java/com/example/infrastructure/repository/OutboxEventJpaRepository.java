package com.example.infrastructure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.domain.outbox.OutboxEvent;
import com.example.domain.outbox.OutboxStatus;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

	List<OutboxEvent> findByStatus(OutboxStatus status);

	@Query("SELECT o FROM OutboxEvent o WHERE o.status = :status ORDER BY o.createdAt ASC LIMIT :limit")
	List<OutboxEvent> findByStatusWithLimit(@Param("status") OutboxStatus status, @Param("limit") int limit);
}
