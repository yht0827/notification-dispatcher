package com.example.infrastructure.config.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.config.stream.NotificationStreamProperties;
import com.example.infrastructure.config.stream.OutboxProperties;
import com.example.infrastructure.config.stream.RecoveryProperties;

@Configuration
@EnableConfigurationProperties({NotificationStreamProperties.class, OutboxProperties.class, RecoveryProperties.class})
public class RedisConfig {

	@Bean
	public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}
}
