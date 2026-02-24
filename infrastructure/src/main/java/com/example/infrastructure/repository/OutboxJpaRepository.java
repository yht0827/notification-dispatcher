package com.example.infrastructure.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.domain.outbox.Outbox;
import com.example.domain.outbox.OutboxStatus;

public interface OutboxJpaRepository extends JpaRepository<Outbox, Long> {

	List<Outbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

	void deleteByAggregateId(Long aggregateId);
}
