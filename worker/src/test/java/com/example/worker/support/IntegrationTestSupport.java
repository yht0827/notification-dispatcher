package com.example.worker.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.worker.TestApplication;
import com.example.worker.config.TestcontainersConfig;

/**
 * 통합 테스트 공통 베이스 클래스.
 * 싱글턴 컨테이너를 사용하여 MySQL과 Redis를 제공합니다.
 */
@EnabledIfDockerAvailable
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
public abstract class IntegrationTestSupport {
}
