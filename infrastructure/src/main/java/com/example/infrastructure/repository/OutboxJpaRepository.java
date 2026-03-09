package com.example.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.domain.outbox.Outbox;
import com.example.domain.outbox.OutboxStatus;

public interface OutboxJpaRepository extends JpaRepository<Outbox, Long> {

	List<Outbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

	@Query("SELECT o FROM Outbox o WHERE o.status = :status AND (o.scheduledAt IS NULL OR o.scheduledAt <= :now) ORDER BY o.createdAt ASC")
	List<Outbox> findReadyByStatus(@Param("status") OutboxStatus status, @Param("now") LocalDateTime now,
		Pageable pageable);

	void deleteByAggregateId(Long aggregateId);
}
