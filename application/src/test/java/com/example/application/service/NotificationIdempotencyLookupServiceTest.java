package com.example.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.service.mapper.NotificationCommandResultMapper;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationGroup;

@ExtendWith(MockitoExtension.class)
class NotificationIdempotencyLookupServiceTest {

	@Mock
	private NotificationGroupRepository groupRepository;

	@Mock
	private NotificationCommandResultMapper resultMapper;

	private NotificationIdempotencyLookupService service;

	@BeforeEach
	void setUp() {
		service = new NotificationIdempotencyLookupService(groupRepository, resultMapper);
	}

	@Test
	@DisplayName("idempotency key가 null이면 저장소를 조회하지 않는다")
	void findExistingResult_returnsEmptyWhenKeyIsNull() {
		assertThat(service.findExistingResult("client", null)).isEmpty();
		verify(groupRepository, never()).findByClientIdAndIdempotencyKey("client", null);
	}

	@Test
	@DisplayName("기존 그룹이 있으면 command result로 매핑해 반환한다")
	void findExistingResult_returnsMappedResult() {
		NotificationGroup group = NotificationGroup.create(
			"client",
			"idem-1",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			1
		);
		NotificationCommandResult result = new NotificationCommandResult(10L, 1);
		when(groupRepository.findByClientIdAndIdempotencyKey("client", "idem-1")).thenReturn(Optional.of(group));
		when(resultMapper.toResult(group)).thenReturn(result);

		assertThat(service.findExistingResult("client", "idem-1")).contains(result);
		verify(resultMapper).toResult(group);
	}

	@Test
	@DisplayName("collision recovery 조회도 동일하게 기존 그룹을 반환한다")
	void findExistingResultAfterCollision_returnsMappedResult() {
		NotificationGroup group = NotificationGroup.create(
			"client",
			"idem-2",
			"sender",
			"title",
			"content",
			ChannelType.EMAIL,
			2
		);
		NotificationCommandResult result = new NotificationCommandResult(20L, 2);
		when(groupRepository.findByClientIdAndIdempotencyKey("client", "idem-2")).thenReturn(Optional.of(group));
		when(resultMapper.toResult(group)).thenReturn(result);

		assertThat(service.findExistingResultAfterCollision("client", "idem-2")).contains(result);
		verify(resultMapper).toResult(group);
	}
}
