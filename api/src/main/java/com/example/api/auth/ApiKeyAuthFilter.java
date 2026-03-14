package com.example.api.auth;

import java.io.IOException;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String CLIENT_ID_ATTRIBUTE = "clientId";
	public static final String HEADER_API_KEY = "X-Api-Key";

	private final Set<String> validKeys;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/api/admin/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain) throws ServletException, IOException {
		String apiKey = request.getHeader(HEADER_API_KEY);
		if (apiKey == null || apiKey.isBlank()) {
			writeUnauthorized(response, "X-Api-Key 헤더가 필요합니다.");
			return;
		}

		if (!validKeys.contains(apiKey)) {
			writeUnauthorized(response, "유효하지 않은 API Key입니다.");
			return;
		}

		request.setAttribute(CLIENT_ID_ATTRIBUTE, apiKey);
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
