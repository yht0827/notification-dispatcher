package com.example.infrastructure.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.application.port.out.result.SendResult;
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
