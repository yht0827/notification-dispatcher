package com.example.application.port.out;

import java.util.List;

import com.example.domain.outbox.OutboxEvent;
import com.example.domain.outbox.OutboxStatus;

public interface OutboxEventRepository {

	OutboxEvent save(OutboxEvent event);

	List<OutboxEvent> findByStatus(OutboxStatus status);

	List<OutboxEvent> findByStatusWithLimit(OutboxStatus status, int limit);
}
