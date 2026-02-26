package com.example.infrastructure.stream.support;

import java.util.Optional;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStreamCommands;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Lettuce 네이티브 연결에서 RedisStreamCommands를 추출하는 유틸리티.
 * StringRedisTemplate 사용 시 항상 {@code <String, String>} 타입이므로 캐스팅은 안전하다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LettuceStreamCommandsExtractor {

	@SuppressWarnings("unchecked")
	public static Optional<RedisStreamCommands<String, String>> extract(Object nativeConnection) {
		if (nativeConnection instanceof StatefulRedisConnection<?, ?> connection) {
			return Optional.of((RedisStreamCommands<String, String>) connection.sync());
		}
		if (nativeConnection instanceof StatefulRedisClusterConnection<?, ?> connection) {
			return Optional.of((RedisStreamCommands<String, String>) connection.sync());
		}
		return Optional.empty();
	}
}
