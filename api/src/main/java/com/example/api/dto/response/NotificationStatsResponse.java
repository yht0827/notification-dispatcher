package com.example.api.dto.response;

import com.example.application.port.in.result.NotificationStatsResult;

public record NotificationStatsResponse(
	long pending,
	long sending,
	long sent,
	long failed,
	long canceled,
	long total
) {
	public static NotificationStatsResponse from(NotificationStatsResult result) {
		return new NotificationStatsResponse(
			result.pending(), result.sending(), result.sent(),
			result.failed(), result.canceled(), result.total()
		);
	}
}
