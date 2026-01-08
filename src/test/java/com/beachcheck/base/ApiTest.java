package com.beachcheck.base;

import com.beachcheck.config.TestcontainersConfig;
import com.beachcheck.domain.User;
import com.beachcheck.util.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Why: API (Controller) 테스트 공통 설정 중앙 관리
 * Policy: MockMvc + 실제 DB + JWT 인증
 * Contract(Input): 모든 API 테스트 클래스가 이를 상속
 * Contract(Output): HTTP 요청/응답 테스트 가능
 */
@SpringBootTest
@AutoConfigureMockMvc              // MockMvc 자동 설정
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
public abstract class ApiTest {

    @Autowired
    protected MockMvc mockMvc; // HTTP 요청/응답 테스트용

    @Autowired
    protected ObjectMapper objectMapper;  // JSON 직렬화/역직렬화용

    @Autowired
    protected JwtUtils jwtUtils;      // JWT 토큰 생성/검증용

    /**
     * JWT 토큰 생성 헬퍼 메서드
     * Why: 인증된 요청 테스트 시 사용
     */
    protected String generateToken(User user) {
        return jwtUtils.generateAccessToken(user);
    }

    /**
     * Authorization 헤더 생성
     */
    protected String authHeader(User user) {
        return "Bearer " + generateToken(user);
    }
}

