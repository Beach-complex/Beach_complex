package com.beachcheck.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("MdcRequestFilter 단위 테스트 — ADR-009 requestId MDC 주입/정리")
class MdcRequestFilterTest {

  private static final String MDC_KEY = "requestId";
  private static final String HEADER = "X-Request-Id";

  private final MdcRequestFilter filter = new MdcRequestFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Nested
  @DisplayName("정상 흐름")
  class HappyPath {

    @Test
    @DisplayName("요청에 X-Request-Id 헤더가 있으면 그 값을 그대로 MDC와 응답 헤더에 사용한다")
    void givenIncomingHeader_whenFilter_thenReuseProvidedId() throws Exception {
      // given
      String incoming = "req-from-gateway-12345";
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader(HEADER, incoming);
      MockHttpServletResponse response = new MockHttpServletResponse();
      CapturingFilterChain chain = new CapturingFilterChain();

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(chain.capturedRequestId).isEqualTo(incoming);
      assertThat(response.getHeader(HEADER)).isEqualTo(incoming);
    }

    @Test
    @DisplayName("X-Request-Id 헤더가 없으면 UUID를 생성해 MDC와 응답 헤더에 동일 ID를 박는다")
    void givenNoHeader_whenFilter_thenGenerateUuidAndEcho() throws Exception {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();
      CapturingFilterChain chain = new CapturingFilterChain();

      // when
      filter.doFilter(request, response, chain);

      // then — UUID는 36자, 응답 헤더와 동일
      assertThat(chain.capturedRequestId).isNotBlank().hasSize(36);
      assertThat(response.getHeader(HEADER)).isEqualTo(chain.capturedRequestId);
    }

    @Test
    @DisplayName("빈 문자열 헤더는 빈 값으로 신뢰하지 않고 UUID로 생성한다")
    void givenBlankHeader_whenFilter_thenGenerateUuid() throws Exception {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader(HEADER, "   ");
      MockHttpServletResponse response = new MockHttpServletResponse();
      CapturingFilterChain chain = new CapturingFilterChain();

      // when
      filter.doFilter(request, response, chain);

      // then
      assertThat(chain.capturedRequestId).isNotBlank().hasSize(36);
    }
  }

  @Nested
  @DisplayName("MDC 누수 방지")
  class MdcCleanup {

    @Test
    @DisplayName("필터 통과 후 MDC에서 requestId가 제거된다 (스레드풀 재사용 누수 방지)")
    void afterFilter_mdcIsCleared() throws Exception {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      // when
      filter.doFilter(request, response, new MockFilterChain());

      // then
      assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("체인 실행 중 예외가 발생해도 MDC가 정리된다 (try-finally 보장)")
    void exceptionInChain_mdcStillCleared() {
      // given
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain blowingChain =
          (req, res) -> {
            throw new RuntimeException("boom");
          };

      // when
      Throwable thrown = catchThrowable(() -> filter.doFilter(request, response, blowingChain));

      // then
      assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessage("boom");
      assertThat(MDC.get(MDC_KEY)).isNull();
    }
  }

  /** 체인 실행 시점의 MDC 값을 캡처해, 필터가 doFilter 호출 전에 MDC를 박았는지 검증한다. */
  private static final class CapturingFilterChain implements FilterChain {
    String capturedRequestId;

    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
        throws IOException, ServletException {
      capturedRequestId = MDC.get(MDC_KEY);
    }
  }
}
