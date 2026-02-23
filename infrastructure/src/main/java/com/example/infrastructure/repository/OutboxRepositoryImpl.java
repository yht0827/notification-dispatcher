package com.example.infrastructure.repository;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.example.application.port.out.OutboxRepository;
import com.example.domain.outbox.Outbox;
import com.example.domain.outbox.OutboxStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OutboxRepositoryImpl implements OutboxRepository {

	private final OutboxJpaRepository jpaRepository;

	@Override
	public Outbox save(Outbox outbox) {
		return jpaRepository.save(outbox);
	}

	@Override
	public List<Outbox> saveAll(List<Outbox> outboxes) {
		return jpaRepository.saveAll(outboxes);
	}

	@Override
	public List<Outbox> findByStatus(OutboxStatus status, int limit) {
		return jpaRepository.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, limit));
	}

	@Override
	public void delete(Outbox outbox) {
		jpaRepository.delete(outbox);
	}

	@Override
	public void deleteAll(List<Outbox> outboxes) {
		jpaRepository.deleteAll(outboxes);
	}
}
