package com.beachcheck.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Why: ADR-009 로깅 표준 — 요청 단위 상관관계 ID(requestId)를 MDC에 주입해, 로그 한 줄만 보고도 동일 요청 흐름을 추적할 수 있게 한다.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>외부에서 {@code X-Request-Id} 헤더를 보내면 그대로 사용(게이트웨이/LB와 ID 통합)
 *   <li>없으면 UUID로 생성
 *   <li>응답 헤더에 동일 ID를 실어 클라이언트가 장애 신고 시 본인 요청 ID를 알 수 있게 함
 *   <li>{@code finally}에서 반드시 MDC 정리 — 스레드풀 재사용 시 다음 요청에 누수 방지
 * </ul>
 *
 * <p>Contract(Input): 임의의 HTTP 요청. {@code X-Request-Id} 헤더는 선택.
 *
 * <p>Contract(Output): 응답 헤더 {@code X-Request-Id} 항상 세팅. MDC 키 {@code requestId} 가 요청 처리 도중에만 유효하고,
 * 요청 종료 후에는 반드시 제거됨.
 *
 * <p>TODO: traceId/spanId는 트레이싱 SDK(ADR-011)가 자동 주입할 예정이므로 본 필터에서는 다루지 않는다. userId는 인증 직후 {@link
 * com.beachcheck.global.security.JwtAuthenticationFilter}에서 주입한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcRequestFilter extends OncePerRequestFilter {

  private static final String MDC_REQUEST_ID = "requestId";
  private static final String HEADER_REQUEST_ID = "X-Request-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String requestId = request.getHeader(HEADER_REQUEST_ID);
    if (!StringUtils.hasText(requestId)) {
      requestId = UUID.randomUUID().toString();
    }

    MDC.put(MDC_REQUEST_ID, requestId);
    response.setHeader(HEADER_REQUEST_ID, requestId);

    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_REQUEST_ID);
    }
  }
}
