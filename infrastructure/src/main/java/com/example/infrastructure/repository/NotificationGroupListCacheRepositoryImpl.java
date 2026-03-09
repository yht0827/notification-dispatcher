package com.example.infrastructure.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.example.application.port.in.result.NotificationGroupResult;
import com.example.application.port.out.cache.NotificationGroupListCacheRepository;
import com.example.infrastructure.config.redis.NotificationCacheProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationGroupListCacheRepositoryImpl implements NotificationGroupListCacheRepository {

	private static final TypeReference<List<NotificationGroupResult>> GROUP_LIST_TYPE = new TypeReference<>() {
	};

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final NotificationCacheProperties cacheProperties;

	@Override
	public boolean enabled() {
		return cacheProperties.groupListEnabled();
	}

	@Override
	public Optional<List<NotificationGroupResult>> getLatest(String clientId) {
		try {
			String value = redisTemplate.opsForValue().get(key(clientId));
			if (value == null || value.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(objectMapper.readValue(value, GROUP_LIST_TYPE));
		} catch (Exception e) {
			log.warn("group list cache 조회 실패: clientId={}", clientId, e);
			return Optional.empty();
		}
	}

	@Override
	public void putLatest(String clientId, List<NotificationGroupResult> groups) {
		try {
			redisTemplate.opsForValue().set(
				key(clientId),
				objectMapper.writeValueAsString(groups),
				cacheProperties.groupListTtl()
			);
		} catch (Exception e) {
			log.warn("group list cache 저장 실패: clientId={}", clientId, e);
		}
	}

	@Override
	public void evictLatest(String clientId) {
		try {
			redisTemplate.delete(key(clientId));
		} catch (Exception e) {
			log.warn("group list cache 삭제 실패: clientId={}", clientId, e);
		}
	}

	@Override
	public int latestLimit() {
		return cacheProperties.groupListLatestLimit();
	}

	private String key(String clientId) {
		return "notification:group-list:%s:latest-%d".formatted(
			clientId,
			cacheProperties.groupListLatestLimit()
		);
	}
}
