package com.example.infrastructure.repository;

import java.util.Optional;

import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.example.application.port.in.result.NotificationGroupDetailResult;
import com.example.application.port.out.cache.NotificationGroupDetailCacheRepository;
import com.example.infrastructure.config.redis.NotificationCacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationGroupDetailCacheRepositoryImpl implements NotificationGroupDetailCacheRepository {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final NotificationCacheProperties cacheProperties;

	@Override
	public boolean enabled() {
		return cacheProperties.groupDetailEnabled();
	}

	@Override
	public Optional<NotificationGroupDetailResult> get(Long groupId) {
		try {
			String value = redisTemplate.opsForValue().get(key(groupId));
			if (value == null) {
				return Optional.empty();
			}
			return Optional.of(objectMapper.readValue(value, NotificationGroupDetailResult.class));
		} catch (RedisSystemException | JsonProcessingException e) {
			log.warn("group detail cache 조회 실패: groupId={}", groupId, e);
			return Optional.empty();
		}
	}

	@Override
	public void put(Long groupId, NotificationGroupDetailResult detail) {
		try {
			redisTemplate.opsForValue().set(
				key(groupId),
				objectMapper.writeValueAsString(detail),
				cacheProperties.groupDetailTtl()
			);
		} catch (RedisSystemException | JsonProcessingException e) {
			log.warn("group detail cache 저장 실패: groupId={}", groupId, e);
		}
	}

	@Override
	public void evict(Long groupId) {
		try {
			redisTemplate.delete(key(groupId));
		} catch (RedisSystemException e) {
			log.warn("group detail cache 삭제 실패: groupId={}", groupId, e);
		}
	}

	private String key(Long groupId) {
		return "notification:group-detail:" + groupId;
	}
}
