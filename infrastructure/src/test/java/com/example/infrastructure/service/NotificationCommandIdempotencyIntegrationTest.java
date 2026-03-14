package com.example.infrastructure.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.application.port.in.NotificationWriteUseCase;
import com.example.application.port.in.command.SendCommand;
import com.example.application.port.in.result.NotificationCommandResult;
import com.example.application.port.out.repository.NotificationGroupRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationGroup;
import com.example.infrastructure.support.IntegrationTestSupport;

class NotificationCommandIdempotencyIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private NotificationWriteUseCase commandUseCase;

	@Autowired
	private NotificationGroupRepository groupRepository;

	@Test
	@DisplayName("동일 clientId/idempotencyKey로 재요청하면 기존 그룹을 반환한다")
	void send_withSameIdempotencyKey_returnsExistingGroup() {
		// given
		SendCommand command = new SendCommand(
			"idem-test-client",
			"MyShop",
			"중복요청 테스트",
			"같은 요청은 한 번만 처리되어야 합니다.",
			ChannelType.EMAIL,
			List.of("user1@example.com", "user2@example.com"),
			"idem-integration-1001",
			null
		);

		// when
		NotificationCommandResult first = commandUseCase.request(command);
		NotificationCommandResult second = commandUseCase.request(command);

		// then
		assertThat(second.groupId()).isEqualTo(first.groupId());
		assertThat(second.totalCount()).isEqualTo(2);
		NotificationGroup stored = groupRepository
			.findByClientIdAndIdempotencyKey("idem-test-client", "idem-integration-1001")
			.orElseThrow();
		assertThat(stored.getId()).isEqualTo(first.groupId());
	}
}
