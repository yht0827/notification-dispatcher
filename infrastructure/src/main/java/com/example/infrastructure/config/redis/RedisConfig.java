package com.example.infrastructure.config.redis;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.config.stream.NotificationStreamProperties;
import com.example.infrastructure.polling.OutboxProperties;
import com.example.infrastructure.polling.RecoveryProperties;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;

@Configuration
@EnableConfigurationProperties({NotificationStreamProperties.class, OutboxProperties.class, RecoveryProperties.class})
public class RedisConfig {

	@Bean
	public LettuceConnectionFactory lettuceConnectionFactory(RedisProperties redisProperties) {
		return new LettuceConnectionFactory(redisProperties.getHost(), redisProperties.getPort());
	}

	@Bean
	public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
		return new StringRedisTemplate(lettuceConnectionFactory);
	}

	@Bean(destroyMethod = "shutdown")
	public RedisClient lettuceRedisClient(RedisProperties redisProperties) {
		return RedisClient.create(RedisURI.create(redisProperties.getHost(), redisProperties.getPort()));
	}

	@Bean(destroyMethod = "close")
	public StatefulRedisConnection<String, String> lettuceStreamConnection(RedisClient lettuceRedisClient) {
		return lettuceRedisClient.connect();
	}
}
