package com.example.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.service.mapper.NotificationCommandResultMapper;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.repository.NotificationGroupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationIdempotencyLookupService {

	private final NotificationGroupRepository groupRepository;
	private final NotificationCommandResultMapper resultMapper;

	@Transactional
	public Optional<NotificationCommandResult> findExistingResult(String clientId, String idempotencyKey) {
		return doFindExistingResult(clientId, idempotencyKey);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<NotificationCommandResult> findExistingResultAfterCollision(String clientId,
		String idempotencyKey) {
		return doFindExistingResult(clientId, idempotencyKey);
	}

	private Optional<NotificationCommandResult> doFindExistingResult(String clientId, String idempotencyKey) {
		if (idempotencyKey == null) {
			return Optional.empty();
		}
		return groupRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
			.map(resultMapper::toResult);
	}
}
