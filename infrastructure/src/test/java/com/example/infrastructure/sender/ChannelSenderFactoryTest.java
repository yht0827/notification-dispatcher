package com.example.infrastructure.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.domain.exception.UnsupportedChannelException;
import com.example.domain.notification.ChannelType;
import com.example.infrastructure.sender.exception.DuplicateChannelSenderRegistrationException;

class ChannelSenderFactoryTest {

	@Test
	@DisplayName("채널 타입에 맞는 sender를 반환한다")
	void getSender_returnsMappedSender() {
		ChannelSender emailSender = sender(ChannelType.EMAIL);
		ChannelSender smsSender = sender(ChannelType.SMS);
		ChannelSender kakaoSender = sender(ChannelType.KAKAO);

		ChannelSenderFactory factory = new ChannelSenderFactory(List.of(emailSender, smsSender, kakaoSender));
		factory.init();

		assertThat(factory.getSender(ChannelType.EMAIL)).isSameAs(emailSender);
		assertThat(factory.getSender(ChannelType.SMS)).isSameAs(smsSender);
		assertThat(factory.getSender(ChannelType.KAKAO)).isSameAs(kakaoSender);
	}

	@Test
	@DisplayName("지원하지 않는 채널이면 예외를 던진다")
	void getSender_throwsWhenUnsupportedChannel() {
		ChannelSenderFactory factory = new ChannelSenderFactory(List.of(sender(ChannelType.EMAIL)));
		factory.init();

		assertThatThrownBy(() -> factory.getSender(ChannelType.SMS))
			.isInstanceOf(UnsupportedChannelException.class)
			.hasMessageContaining("지원하지 않는 채널");
	}

	@Test
	@DisplayName("동일 채널 sender가 중복 등록되면 초기화 시 예외를 던진다")
	void init_throwsWhenDuplicateChannelSenderRegistered() {
		ChannelSender first = sender(ChannelType.EMAIL);
		ChannelSender duplicate = sender(ChannelType.EMAIL);

		ChannelSenderFactory factory = new ChannelSenderFactory(List.of(first, duplicate));

		assertThatThrownBy(factory::init)
			.isInstanceOf(DuplicateChannelSenderRegistrationException.class)
			.hasMessageContaining("중복 ChannelSender 등록");
	}

	private ChannelSender sender(ChannelType channelType) {
		ChannelSender sender = mock(ChannelSender.class);
		when(sender.getChannelType()).thenReturn(channelType);
		return sender;
	}
}
