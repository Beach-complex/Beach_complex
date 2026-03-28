package com.beachcheck.client;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * Why: CongestionClient가 구체 구현(AwsSigV4Interceptor)에 직접 의존하지 않도록 추상화. 운영 환경에서는 SigV4 서명 구현체, 테스트
 * 환경에서는 NoOp 구현체를 주입한다.
 */
public interface CongestionInterceptor extends ClientHttpRequestInterceptor {}
