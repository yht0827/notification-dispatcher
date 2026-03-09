package com.example.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.api.exception.GlobalExceptionHandler;
import com.example.application.port.in.NotificationQueryUseCase;
import com.example.application.port.in.NotificationWriteUseCase;

@WebMvcTest(NotificationController.class)
@Import({GlobalExceptionHandler.class, NotificationController.class})
class NotificationControllerValidationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationQueryUseCase queryUseCase;

	@MockitoBean
	private NotificationWriteUseCase writeUseCase;

	@Test
	@DisplayName("요청자별 조회 cursorId가 0이면 400을 반환한다")
	void getGroupsByClientId_returnsBadRequestWhenCursorIdIsZero() throws Exception {
		mockMvc.perform(get("/api/v1/notifications/groups")
				.header("X-Api-Key", "order-service")
				.param("cursorId", "0"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("요청자별 조회 size가 0이면 400을 반환한다")
	void getGroupsByClientId_returnsBadRequestWhenSizeIsZero() throws Exception {
		mockMvc.perform(get("/api/v1/notifications/groups")
				.header("X-Api-Key", "order-service")
				.param("size", "0"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("요청자별 조회 size가 100을 초과하면 400을 반환한다")
	void getGroupsByClientId_returnsBadRequestWhenSizeExceedsMax() throws Exception {
		mockMvc.perform(get("/api/v1/notifications/groups")
				.header("X-Api-Key", "order-service")
				.param("size", "101"))
			.andExpect(status().isBadRequest());
	}
}
