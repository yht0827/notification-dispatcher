package com.example.infrastructure.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.application.port.out.OutboxEventRepository;
import com.example.domain.outbox.OutboxEvent;
import com.example.domain.outbox.OutboxStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

	private final OutboxEventJpaRepository jpaRepository;

	@Override
	public OutboxEvent save(OutboxEvent event) {
		return jpaRepository.save(event);
	}

	@Override
	public List<OutboxEvent> findByStatus(OutboxStatus status) {
		return jpaRepository.findByStatus(status);
	}

	@Override
	public List<OutboxEvent> findByStatusWithLimit(OutboxStatus status, int limit) {
		return jpaRepository.findByStatusWithLimit(status, limit);
	}
}
