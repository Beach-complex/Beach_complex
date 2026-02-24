package com.beachcheck.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("해시 유틸 단위 테스트")
class HashUtilsTest {

  private static final String ABC_SHA256_HEX =
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

  @Test
  @DisplayName("고정 입력 abc의 SHA-256 결과가 표준 벡터와 일치한다")
  void sha256Hex_matchesKnownVector() {
    String hashed = HashUtils.sha256Hex("abc");

    assertThat(hashed).isEqualTo(ABC_SHA256_HEX);
  }

  @Test
  @DisplayName("동일 입력은 항상 동일 해시를 반환한다")
  void sha256Hex_sameInput_returnsSameHash() {
    String first = HashUtils.sha256Hex("same-input");
    String second = HashUtils.sha256Hex("same-input");

    assertThat(first).isEqualTo(second);
  }

  @Test
  @DisplayName("서로 다른 입력은 서로 다른 해시를 반환한다")
  void sha256Hex_differentInput_returnsDifferentHash() {
    String first = HashUtils.sha256Hex("input-a");
    String second = HashUtils.sha256Hex("input-b");

    assertThat(first).isNotEqualTo(second);
  }
}
