package com.example.infrastructure.config.redis;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.infrastructure.polling.OutboxProperties;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class RedisConfig {

	@Bean
	public LettuceConnectionFactory lettuceConnectionFactory(RedisProperties redisProperties) {
		return new LettuceConnectionFactory(redisProperties.getHost(), redisProperties.getPort());
	}

	@Bean
	public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
		return new StringRedisTemplate(lettuceConnectionFactory);
	}
}
