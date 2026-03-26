package com.example.infrastructure.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxRepositoryImplTest {

	@Mock
	private OutboxJpaRepository jpaRepository;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("group outbox 저장은 GROUP aggregate와 payload를 사용해 insert 한다")
	void saveGroupNotificationCreatedEvent_insertsSingleGroupOutbox() {
		OutboxRepositoryImpl repository = new OutboxRepositoryImpl(jpaRepository, jdbcTemplate);
		LocalDateTime now = LocalDateTime.of(2026, 3, 19, 15, 30);

		repository.saveGroupNotificationCreatedEvent(10L, List.of(101L, 102L, 103L), null, now);

		verify(jdbcTemplate).update(any(String.class),
			eq("Group"),
			eq(10L),
			eq("NotificationCreated"),
			eq("101,102,103"),
			eq("PENDING"),
			eq(0),
			eq(null),
			eq(null),
			eq(now),
			eq(now)
		);
	}

	@Test
	@DisplayName("group outbox 저장은 groupId나 notification ids가 없으면 skip 한다")
	void saveGroupNotificationCreatedEvent_skipsWhenInputInvalid() {
		OutboxRepositoryImpl repository = new OutboxRepositoryImpl(jpaRepository, jdbcTemplate);
		LocalDateTime now = LocalDateTime.of(2026, 3, 19, 15, 30);

		repository.saveGroupNotificationCreatedEvent(null, List.of(1L), null, now);
		repository.saveGroupNotificationCreatedEvent(10L, List.of(), null, now);

		verify(jdbcTemplate, never()).update(any(String.class), any(), any(), any(), any(), any(), any(), any(),
			any(), any(), any());
	}

	@Test
	@DisplayName("aggregate ids 삭제는 리스트가 있을 때만 JPA deleteByAggregateIdIn을 호출한다")
	void deleteByAggregateIds_deletesOnlyWhenIdsPresent() {
		OutboxRepositoryImpl repository = new OutboxRepositoryImpl(jpaRepository, jdbcTemplate);

		repository.deleteByAggregateIds(List.of(10L, 20L));
		repository.deleteByAggregateIds(List.of());
		repository.deleteByAggregateIds(null);

		verify(jpaRepository).deleteByAggregateIdIn(List.of(10L, 20L));
		verify(jpaRepository, never()).deleteByAggregateIdIn(List.of());
	}
}
