package com.beachcheck.global.security;

import com.beachcheck.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtils jwtUtils;
  private final UserRepository userRepository;

  // TODO(OAuth): OAuth 클레임/권한 매핑 정책 확정 시 인증 객체 생성 로직 보완.

  public JwtAuthenticationFilter(JwtUtils jwtUtils, UserRepository userRepository) {
    this.jwtUtils = jwtUtils;
    this.userRepository = userRepository;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String p = request.getRequestURI();
    return p != null && p.startsWith("/api/_debug/");
  }

  /**
   * Why: ADR-009 로깅 표준 — 인증 성공 시 {@code userId}를 MDC에 주입해, 이후 로그 라인에 사용자 식별자가 따라붙도록 한다. 스레드풀 재사용
   * 환경에서 누수가 일어나지 않도록 try-finally로 정리한다.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>JWT 검증 실패/사용자 미존재 등 인증 실패 시 MDC에 아무것도 넣지 않음 (이전 요청 잔재도 없음)
   *   <li>인증 성공 시점에만 {@code userIdPushed=true}로 표시하고, finally에서 해당 키만 제거
   *   <li>예외 흐름이 기존과 달라지지 않도록 catch 분기에서도 동일한 finally를 거치게 함
   * </ul>
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    boolean userIdPushed = false;
    try {
      String jwt = getJwtFromRequest(request);

      if (StringUtils.hasText(jwt) && jwtUtils.validateToken(jwt) && jwtUtils.isAccessToken(jwt)) {
        UUID userId = jwtUtils.getUserIdFromToken(jwt);

        UserDetails userDetails =
            userRepository
                .findById(userId)
                .orElseThrow(() -> new RuntimeException("유저를 찾을 수 없습니다."));

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        MDC.put("userId", userId.toString());
        userIdPushed = true;
      }
    } catch (Exception ex) {

      logger.error("보안 컨텍스트에 사용자 인증을 설정할 수 없습니다.", ex);
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      if (userIdPushed) {
        MDC.remove("userId");
      }
    }
  }

  private String getJwtFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }
}
