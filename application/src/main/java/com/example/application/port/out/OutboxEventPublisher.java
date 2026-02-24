package com.example.application.port.out;

import java.util.List;

public interface OutboxEventPublisher {

	void publishAfterCommit(List<Long> notificationIds);
}
