package com.beachcheck.config;

import com.beachcheck.exception.ErrorCode;
import com.beachcheck.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  /**
   * Why: 인증 실패 응답을 ProblemDetail로 통일해 클라이언트 계약을 보호한다.
   *
   * <p>Policy: 401 + application/problem+json + code/title 일관성을 유지한다.
   *
   * <p>Contract(Output): code/title=UNAUTHORIZED, status=401.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (request, response, authException) -> {
                      ProblemDetail problemDetail =
                          ProblemDetail.forStatusAndDetail(
                              HttpStatus.UNAUTHORIZED, "인증이 필요합니다");
                      problemDetail.setTitle(ErrorCode.UNAUTHORIZED.getCode());
                      problemDetail.setProperty("code", ErrorCode.UNAUTHORIZED.getCode());
                      problemDetail.setProperty("details", null);

                      response.setStatus(HttpStatus.UNAUTHORIZED.value());
                      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                      try {
                        objectMapper.writeValue(response.getWriter(), problemDetail);
                      } catch (Exception writeEx) {
                        if (!response.isCommitted()) {
                          response.sendError(HttpStatus.UNAUTHORIZED.value());
                        }
                      }
                    }))
        .authorizeHttpRequests(
            auth ->
                auth
                    // ✅ CORS preflight 허용 (가장 위에)
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()

                    // ✅ 디버그/문서/헬스는 전부 공개
                    .requestMatchers("/api/_debug/**")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/api/docs/**")
                    .permitAll()

                    // ✅ 인증 엔드포인트 공개
                    .requestMatchers(
                        HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/refresh")
                    .permitAll()

                    // ✅ 이메일 링크 진입(GET)과 실제 인증 완료(POST)를 모두 공개 처리한다.
                    .requestMatchers(HttpMethod.GET, "/api/auth/verify-email")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/verify-email/confirm")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/resend-verification")
                    .permitAll()

                    // ✅ 해변 조회는 공개, 찜 토글(임시)도 공개
                    .requestMatchers(HttpMethod.GET, "/api/beaches/reservations")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/beaches/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/favorites/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/favorites/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/favorites/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/favorites/**")
                    .authenticated()

                    // ✅ 관리자만
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")

                    // 그 외는 인증 필요
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  // ✅ CORS 허용(프론트 로컬에서 호출 가능하도록)
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    // 필요시 여기 리스트만 조정
    config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:8080"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
