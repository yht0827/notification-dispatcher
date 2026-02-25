package com.example.infrastructure.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.infrastructure.TestApplication;
import com.example.infrastructure.config.TestcontainersConfig;

/**
 * 통합 테스트 공통 베이스 클래스.
 * 싱글턴 컨테이너를 사용하여 MySQL과 Redis를 제공합니다.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
public abstract class IntegrationTestSupport {
}
