package com.example.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.response.ApiResponse;
import com.example.api.dto.response.NotificationStatsResponse;
import com.example.application.port.in.AdminNotificationStatsUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin", description = "관리자 통계 API")
@RestController
@RequestMapping("/api/admin/v1")
@RequiredArgsConstructor
public class AdminNotificationController {

	private final AdminNotificationStatsUseCase statsUseCase;

	@Operation(summary = "전체 알림 통계 조회")
	@GetMapping("/stats")
	public ApiResponse<NotificationStatsResponse> getStats() {
		return ApiResponse.ok(NotificationStatsResponse.from(statsUseCase.getStats()));
	}

	@Operation(summary = "클라이언트별 알림 통계 조회")
	@GetMapping("/stats/{clientId}")
	public ApiResponse<NotificationStatsResponse> getStatsByClientId(@PathVariable String clientId) {
		return ApiResponse.ok(NotificationStatsResponse.from(statsUseCase.getStatsByClientId(clientId)));
	}
}
