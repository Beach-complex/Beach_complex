package com.beachcheck.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Why: 실제 DB 환경에서 통합 테스트를 실행하기 위해 Testcontainers 설정
 * Policy: PostgreSQL + PostGIS 이미지 사용, 테스트마다 컨테이너 재사용
 * Contract(Input): Docker가 로컬 환경에 설치되어 있어야 함
 * Contract(Output): PostgreSQL 컨테이너를 Spring 컨텍스트에 주입
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /**
     * PostgreSQL + PostGIS 컨테이너 생성
     *
     * @ServiceConnection: Spring Boot가 자동으로 DataSource 설정
     * DockerImageName: postgis/postgis:16-3.4 (PostgreSQL 16 + PostGIS 3.4)
     * withReuse(true): 컨테이너 재사용으로 테스트 속도 향상
     */
    @Bean
    @ServiceConnection // 자동으로 DataSource 설정에 사용
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4")
        )
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true); // 컨테이너 재사용 (로컬 속도 향상)
    }
}

