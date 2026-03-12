package com.example.infrastructure.config.redis;

import java.time.Duration;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import com.example.infrastructure.polling.OutboxProperties;

import io.lettuce.core.api.StatefulConnection;

@Configuration
@EnableConfigurationProperties({OutboxProperties.class})
public class RedisConfig {

	@Bean
	public LettuceConnectionFactory lettuceConnectionFactory(RedisProperties redisProperties) {
		RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
		standaloneConfiguration.setHostName(redisProperties.getHost());
		standaloneConfiguration.setPort(redisProperties.getPort());
		standaloneConfiguration.setDatabase(redisProperties.getDatabase());

		if (StringUtils.hasText(redisProperties.getUsername())) {
			standaloneConfiguration.setUsername(redisProperties.getUsername());
		}
		if (StringUtils.hasText(redisProperties.getPassword())) {
			standaloneConfiguration.setPassword(redisProperties.getPassword());
		}

		RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
		GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
		poolConfig.setMaxTotal(pool.getMaxActive());
		poolConfig.setMaxIdle(pool.getMaxIdle());
		poolConfig.setMinIdle(pool.getMinIdle());
		poolConfig.setMaxWait(pool.getMaxWait());

		LettucePoolingClientConfiguration clientConfiguration = LettucePoolingClientConfiguration.builder()
			.commandTimeout(resolveTimeout(redisProperties.getTimeout()))
			.shutdownTimeout(Duration.ZERO)
			.poolConfig(poolConfig)
			.build();

		return new LettuceConnectionFactory(standaloneConfiguration, clientConfiguration);
	}

	private Duration resolveTimeout(Duration timeout) {
		return timeout != null ? timeout : Duration.ofSeconds(1);
	}

	@Bean
	public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
		return new StringRedisTemplate(lettuceConnectionFactory);
	}
}
