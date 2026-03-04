package com.beachcheck.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Why: @EnableScheduling 단일 활성화 지점.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>@EnableScheduling은 이 클래스에서만 선언
 *   <li>개별 스케줄러는 기능별 클래스에서 관리 (예: OutboxSchedulingConfig, BeachConditionScheduler)
 *   <li>조건부 스케줄러는 개별 스케줄러의 해당 클래스 레벨에 @ConditionalOnProperty 적용
 * </ul>
 */
@Configuration
@EnableScheduling
public class EnableSchedulingConfig {}
