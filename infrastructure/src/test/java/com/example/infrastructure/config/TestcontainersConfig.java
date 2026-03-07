package com.example.infrastructure.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 싱글턴 컨테이너 패턴을 적용한 Testcontainers 설정.
 * 모든 통합 테스트에서 동일한 컨테이너를 재사용하여 테스트 속도를 개선합니다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    private static final MySQLContainer<?> MYSQL_CONTAINER;
    private static final RedisContainer REDIS_CONTAINER;
    private static final RabbitMQContainer RABBITMQ_CONTAINER;

    static {
        MYSQL_CONTAINER = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("notification_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        MYSQL_CONTAINER.start();

        REDIS_CONTAINER = new RedisContainer(DockerImageName.parse("redis:7-alpine"))
                .withReuse(true);
        REDIS_CONTAINER.start();

        RABBITMQ_CONTAINER = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
                .withReuse(true);
        RABBITMQ_CONTAINER.start();
    }

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return MYSQL_CONTAINER;
    }

    @Bean
    @ServiceConnection
    RedisContainer redisContainer() {
        return REDIS_CONTAINER;
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMQContainer() {
        return RABBITMQ_CONTAINER;
    }
}
