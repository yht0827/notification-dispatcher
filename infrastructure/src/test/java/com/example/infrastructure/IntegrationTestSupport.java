package com.example.infrastructure;

import com.example.infrastructure.config.TestcontainersConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class IntegrationTestSupport {
    // 통합 테스트 시 이 클래스를 상속받아 사용
}
