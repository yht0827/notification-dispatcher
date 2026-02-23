package com.example.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.application.port.in.NotificationCommandUseCase;
import com.example.application.port.in.NotificationCommandUseCase.SendCommand;
import com.example.application.port.out.NotificationGroupRepository;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.NotificationGroup;
import com.example.infrastructure.TestApplication;
import com.example.infrastructure.config.TestcontainersConfig;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class NotificationCommandIdempotencyIntegrationTest {

	@Autowired
	private NotificationCommandUseCase commandUseCase;

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
			"idem-integration-1001"
		);

		// when
		NotificationGroup first = commandUseCase.request(command);
		NotificationGroup second = commandUseCase.request(command);

		// then
		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getTotalCount()).isEqualTo(2);
		NotificationGroup stored = groupRepository
			.findByClientIdAndIdempotencyKey("idem-test-client", "idem-integration-1001")
			.orElseThrow();
		assertThat(stored.getId()).isEqualTo(first.getId());
	}
}
