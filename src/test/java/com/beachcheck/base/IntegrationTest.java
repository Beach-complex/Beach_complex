package com.beachcheck.base;

import com.beachcheck.config.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * Why: 통합 테스트 공통 설정을 중앙 관리하여 중복 제거
 * Policy: 실제 DB 사용, 트랜잭션 자동 롤백
 * Contract(Input): 모든 통합 테스트 클래스가 이를 상속
 * Contract(Output): 각 테스트는 깨끗한 DB 상태에서 시작
 */
@SpringBootTest
@Import(TestcontainersConfig.class)  // Testcontainers 설정 임포트
@ActiveProfiles("test")                     // application-test.yml 활성화
@Transactional                              // 각 테스트 후 자동 롤백
public abstract class IntegrationTest { // 추상 클래스 선언(이 클래스로는 직접적인 테스트 실행 불가)

    @Autowired
    protected EntityManager entityManager; // JPA 영속성 컨텍스트 접근용

    /**
     * 각 테스트 전에 영속성 컨텍스트 초기화
     * Why: 이전 테스트의 캐시된 엔티티가 영향을 주지 않도록
     */
    @BeforeEach
    void clearPersistenceContext() {
        entityManager.clear();
    }
}

