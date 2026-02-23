package com.example.application.port.out;

import java.util.List;

import com.example.domain.outbox.Outbox;
import com.example.domain.outbox.OutboxStatus;

public interface OutboxRepository {

	Outbox save(Outbox outbox);

	List<Outbox> saveAll(List<Outbox> outboxes);

	List<Outbox> findByStatus(OutboxStatus status, int limit);

	void delete(Outbox outbox);

	void deleteAll(List<Outbox> outboxes);
}
