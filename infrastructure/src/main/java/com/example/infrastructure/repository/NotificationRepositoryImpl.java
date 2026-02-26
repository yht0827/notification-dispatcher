package com.example.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.example.application.port.out.NotificationRepository;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Notification> findByReceiver(String receiver) {
        return jpaRepository.findByReceiver(receiver);
    }

    @Override
    public List<Notification> findByReceiverAndStatus(String receiver, NotificationStatus status) {
        return jpaRepository.findByReceiverAndStatus(receiver, status);
    }

	@Override
	public List<Notification> findByStatus(NotificationStatus status) {
		return jpaRepository.findByStatus(status);
	}

	@Override
	public List<Notification> findByStatusAndCreatedAtBefore(NotificationStatus status, LocalDateTime threshold, int limit) {
		return jpaRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
			status,
			threshold,
			PageRequest.of(0, limit)
		);
	}

	@Override
	public void delete(Notification notification) {
		jpaRepository.delete(notification);
    }
}
