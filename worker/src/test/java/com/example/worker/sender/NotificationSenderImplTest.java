package com.example.worker.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.out.SendResult;
import com.example.domain.notification.ChannelType;
import com.example.domain.notification.Notification;
import com.example.domain.notification.NotificationGroup;

@ExtendWith(MockitoExtension.class)
class NotificationSenderImplTest {

	@Mock
	private ChannelSenderFactory senderFactory;

	@Mock
	private ChannelSender channelSender;

	@InjectMocks
	private NotificationSenderImpl notificationSender;

	@Test
	@DisplayName("채널 타입에 맞는 sender를 선택해 결과를 그대로 반환한다")
	void send_delegatesToChannelSender() {
		Notification notification = createNotification(ChannelType.EMAIL);
		SendResult expected = SendResult.failNonRetryable("수신자 주소 오류");

		when(senderFactory.getSender(ChannelType.EMAIL)).thenReturn(channelSender);
		when(channelSender.send(notification)).thenReturn(expected);

		SendResult actual = notificationSender.send(notification);

		assertThat(actual).isEqualTo(expected);
		verify(senderFactory).getSender(ChannelType.EMAIL);
		verify(channelSender).send(notification);
	}

	@Test
	@DisplayName("동일 채널 알림 묶음은 sender 한 번 선택 후 sendBatch로 위임한다")
	void sendBatch_delegatesToChannelSenderBatch() {
		Notification first = createNotification(ChannelType.EMAIL);
		Notification second = createNotification(ChannelType.EMAIL);

		Map<Long, SendResult> expected = new LinkedHashMap<>();
		expected.put(first.getId(), SendResult.success());
		expected.put(second.getId(), SendResult.failRetryable("temporary"));

		when(senderFactory.getSender(ChannelType.EMAIL)).thenReturn(channelSender);
		when(channelSender.sendBatch(anyList())).thenReturn(expected);

		Map<Long, SendResult> actual = notificationSender.sendBatch(List.of(first, second));

		assertThat(actual).isEqualTo(expected);
		verify(senderFactory).getSender(ChannelType.EMAIL);
		verify(channelSender).sendBatch(List.of(first, second));
	}

	private Notification createNotification(ChannelType channelType) {
		NotificationGroup group = NotificationGroup.create(
			"sender-test",
			"group-idem",
			"MyShop",
			"테스트",
			"테스트 내용",
			channelType,
			1
		);
		return group.addNotification("user@example.com");
	}
}
