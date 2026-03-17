package com.beachcheck.dto.beach.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BeachSearchRequestDto 단위 테스트")
class BeachSearchRequestDtoTest {

  @Test
  @DisplayName("lat lon radiusKm가 모두 있으면 complete radius params다")
  void hasCompleteRadiusParams_returnsTrueWhenAllParamsExist() {
    BeachSearchRequestDto request = new BeachSearchRequestDto(null, null, 37.5, 129.1, 3.0);

    assertThat(request.hasCompleteRadiusParams()).isTrue();
    assertThat(request.hasAnyRadiusParam()).isTrue();
  }

  @Test
  @DisplayName("반경 파라미터가 하나라도 빠지면 complete radius params가 아니다")
  void hasCompleteRadiusParams_returnsFalseWhenAnyParamMissing() {
    BeachSearchRequestDto request = new BeachSearchRequestDto(null, null, 37.5, null, 3.0);

    assertThat(request.hasCompleteRadiusParams()).isFalse();
    assertThat(request.hasAnyRadiusParam()).isTrue();
  }

  @Test
  @DisplayName("반경 파라미터가 모두 없으면 any radius param도 아니다")
  void hasAnyRadiusParam_returnsFalseWhenNoRadiusParamsExist() {
    BeachSearchRequestDto request = new BeachSearchRequestDto("query", "tag", null, null, null);

    assertThat(request.hasAnyRadiusParam()).isFalse();
    assertThat(request.hasCompleteRadiusParams()).isFalse();
  }

  @Test
  @DisplayName("반경 파라미터가 모두 있거나 모두 없으면 검증을 통과한다")
  void validateRadiusParams_allOrNothing_isAllowed() {
    BeachSearchRequestDto complete = new BeachSearchRequestDto(null, null, 37.5, 129.1, 3.0);
    BeachSearchRequestDto empty = new BeachSearchRequestDto("query", "tag", null, null, null);

    assertThatNoException().isThrownBy(complete::validateRadiusParams);
    assertThatNoException().isThrownBy(empty::validateRadiusParams);
  }

  @Test
  @DisplayName("반경 파라미터가 일부만 있으면 예외를 던진다")
  void validateRadiusParams_partialParams_throwsIllegalArgumentException() {
    BeachSearchRequestDto request = new BeachSearchRequestDto(null, null, null, 129.1, 3.0);

    assertThatThrownBy(request::validateRadiusParams)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Radius search requires all three parameters: lat, lon, radiusKm");
  }
}
