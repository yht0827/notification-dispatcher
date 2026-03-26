package com.example.worker.messaging.inbound;

public record MessageProcessDecision(long deliveryTag, ChannelAction action) {

	public static MessageProcessDecision ack(long deliveryTag) {
		return new MessageProcessDecision(deliveryTag, ChannelAction.ACK);
	}

	public static MessageProcessDecision nack(long deliveryTag) {
		return new MessageProcessDecision(deliveryTag, ChannelAction.NACK);
	}

	public boolean shouldAck() {
		return action == ChannelAction.ACK;
	}

	public enum ChannelAction {
		ACK,
		NACK
	}
}
