package com.example.infrastructure.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.example.infrastructure.TestApplication;
import com.example.infrastructure.config.MockMessagingConfig;
import com.example.infrastructure.config.TestcontainersConfig;

/**
 * 트랜잭션 없는 통합 테스트 공통 베이스 클래스.
 * 동시성 테스트나 트랜잭션 경계 테스트에 사용합니다.
 */
@EnabledIfDockerAvailable
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, MockMessagingConfig.class})
public abstract class IntegrationTestSupportNoTx {
}
