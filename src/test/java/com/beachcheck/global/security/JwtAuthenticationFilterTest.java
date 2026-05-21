package com.beachcheck.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.beachcheck.support.fixture.UserTestFixtures;
import com.beachcheck.user.domain.User;
import com.beachcheck.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter 단위 테스트 — ADR-009 userId MDC 주입/정리")
class JwtAuthenticationFilterTest {

  private static final String VALID_JWT = "valid.jwt.token";
  private static final String MDC_KEY = "userId";

  @Mock private JwtUtils jwtUtils;
  @Mock private UserRepository userRepository;

  private JwtAuthenticationFilter filter;

  @AfterEach
  void tearDown() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  private void newFilter() {
    filter = new JwtAuthenticationFilter(jwtUtils, userRepository);
  }

  @Nested
  @DisplayName("인증 성공")
  class AuthenticationSucceeds {

    @Test
    @DisplayName("정상 JWT 인증 시 체인 실행 도중에는 MDC userId가 채워지고, 종료 후엔 제거된다")
    void givenValidJwt_whenFilter_thenMdcSetDuringChainAndClearedAfter() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      User user = UserTestFixtures.createUser("jwt-mdc@example.com");
      user.setId(userId);

      given(jwtUtils.validateToken(VALID_JWT)).willReturn(true);
      given(jwtUtils.isAccessToken(VALID_JWT)).willReturn(true);
      given(jwtUtils.getUserIdFromToken(VALID_JWT)).willReturn(userId);
      given(userRepository.findById(userId)).willReturn(Optional.of(user));
      newFilter();

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer " + VALID_JWT);
      MockHttpServletResponse response = new MockHttpServletResponse();
      MdcSnapshotChain chain = new MdcSnapshotChain();

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(chain.userIdDuringChain).isEqualTo(userId.toString());
      assertThat(MDC.get(MDC_KEY)).isNull();
    }
  }

  @Nested
  @DisplayName("인증 미수행 / 실패 시 MDC 무변경")
  class NoAuthentication {

    @Test
    @DisplayName("Authorization 헤더가 없으면 MDC에 userId가 박히지 않고 종료 후에도 비어있다")
    void givenNoAuthorizationHeader_whenFilter_thenMdcUntouched() throws Exception {
      // given
      newFilter();
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();
      MdcSnapshotChain chain = new MdcSnapshotChain();

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(chain.userIdDuringChain).isNull();
      assertThat(MDC.get(MDC_KEY)).isNull();
      verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("JWT 검증이 실패하면 MDC에 userId가 박히지 않고, 체인은 계속 진행된다")
    void givenInvalidJwt_whenFilter_thenMdcUntouchedAndChainProceeds() throws Exception {
      // given
      given(jwtUtils.validateToken(VALID_JWT)).willReturn(false);
      newFilter();

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer " + VALID_JWT);
      MockHttpServletResponse response = new MockHttpServletResponse();
      MdcSnapshotChain chain = new MdcSnapshotChain();

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(chain.invoked).isTrue();
      assertThat(chain.userIdDuringChain).isNull();
      assertThat(MDC.get(MDC_KEY)).isNull();
    }
  }

  @Nested
  @DisplayName("외부 컴포넌트가 미리 박아둔 MDC 보존")
  class PreExistingMdc {

    @Test
    @DisplayName("필터 진입 전에 외부에서 박아둔 userId는 인증 실패 케이스에서 지워지지 않는다 (userIdPushed 가드 검증)")
    void preExistingUserId_isNotErasedOnAuthFailure() throws Exception {
      // given
      String externallyPushed = "external-user-id";
      MDC.put(MDC_KEY, externallyPushed);
      newFilter();

      MockHttpServletRequest request = new MockHttpServletRequest();
      // Authorization 헤더 없음 → 인증 시도 안 함
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      filter.doFilter(request, response, new MockFilterChain());

      // then — 우리 필터는 박지 않았으므로 지우지도 않음
      assertThat(MDC.get(MDC_KEY)).isEqualTo(externallyPushed);
    }
  }

  @Nested
  @DisplayName("MDC 누수 방지")
  class MdcCleanup {

    @Test
    @DisplayName("체인 실행 중 예외가 발생해도 인증으로 박힌 MDC userId는 finally에서 제거된다")
    void exceptionInChain_mdcStillCleared() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      User user = UserTestFixtures.createUser("cleanup@example.com");
      user.setId(userId);

      given(jwtUtils.validateToken(VALID_JWT)).willReturn(true);
      given(jwtUtils.isAccessToken(VALID_JWT)).willReturn(true);
      given(jwtUtils.getUserIdFromToken(VALID_JWT)).willReturn(userId);
      given(userRepository.findById(userId)).willReturn(Optional.of(user));
      newFilter();

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer " + VALID_JWT);
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain blowingChain =
          (req, res) -> {
            throw new RuntimeException("controller exploded");
          };

      // when
      Throwable thrown = catchThrowable(() -> filter.doFilter(request, response, blowingChain));

      // then
      assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessage("controller exploded");
      assertThat(MDC.get(MDC_KEY)).isNull();
    }
  }

  /** 체인 실행 시점의 MDC userId 값과 호출 여부를 캡처. */
  private static final class MdcSnapshotChain implements FilterChain {
    boolean invoked;
    String userIdDuringChain;

    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
        throws IOException, ServletException {
      invoked = true;
      userIdDuringChain = MDC.get(MDC_KEY);
    }
  }
}
