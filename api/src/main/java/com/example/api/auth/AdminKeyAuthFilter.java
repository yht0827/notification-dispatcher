package com.example.api.auth;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AdminKeyAuthFilter extends OncePerRequestFilter {

	public static final String HEADER_ADMIN_KEY = "X-Admin-Key";

	private final String adminKey;

	@Override
	protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain) throws ServletException, IOException {
		String key = request.getHeader(HEADER_ADMIN_KEY);
		if (key == null || key.isBlank()) {
			writeUnauthorized(response, "X-Admin-Key 헤더가 필요합니다.");
			return;
		}

		if (!adminKey.equals(key)) {
			writeUnauthorized(response, "유효하지 않은 Admin Key입니다.");
			return;
		}

		filterChain.doFilter(request, response);
	}

	private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write(
			"{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}"
		);
	}
}
