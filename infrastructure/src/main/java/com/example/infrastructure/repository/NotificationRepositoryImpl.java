package com.example.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.application.port.out.repository.NotificationRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

	private final NotificationJpaRepositoryAdapter jpaAdapter;
	private final NotificationJdbcBulkRepository jdbcBulkRepository;

	@Override
	public Notification save(Notification notification) {
		return jpaAdapter.save(notification);
	}

	@Override
	public List<Notification> saveAll(List<Notification> notifications) {
		return jpaAdapter.saveAll(notifications);
	}

	@Override
	public List<Long> bulkInsertPending(Long groupId, List<String> receivers, LocalDateTime createdAt) {
		return jdbcBulkRepository.bulkInsertPending(groupId, receivers, createdAt);
	}

	@Override
	public Optional<Notification> findById(Long id) {
		return jpaAdapter.findById(id);
	}

	@Override
	public List<Notification> findAllByIdIn(List<Long> ids) {
		return jpaAdapter.findAllByIdIn(ids);
	}

	@Override
	public long countUnreadByClientIdAndReceiver(String clientId, String receiver, LocalDateTime from) {
		return jpaAdapter.countUnreadByClientIdAndReceiver(clientId, receiver, from);
	}

	@Override
	public List<Notification> findByClientIdAndReceiverWithCursor(
		String clientId,
		String receiver,
		LocalDateTime from,
		Long cursorId,
		int limit
	) {
		return jpaAdapter.findByClientIdAndReceiverWithCursor(clientId, receiver, from, cursorId, limit);
	}

	@Override
	public List<Notification> findByStatus(NotificationStatus status) {
		return jpaAdapter.findByStatus(status);
	}

	@Override
	public List<Notification> findByStatusAndCreatedAtBefore(
		NotificationStatus status,
		LocalDateTime threshold,
		int limit
	) {
		return jpaAdapter.findByStatusAndCreatedAtBefore(status, threshold, limit);
	}

	@Override
	public void delete(Notification notification) {
		jpaAdapter.delete(notification);
	}
}
