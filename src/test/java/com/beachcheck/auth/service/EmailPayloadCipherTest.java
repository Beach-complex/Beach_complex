package com.beachcheck.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailPayloadCipherTest {
  @Test
  void encryptsWithRandomNonceAndDetectsWrongKey() {
    var cipher = new EmailPayloadCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    String input = "https://example.com/verify?token=secret";
    String encrypted = cipher.encrypt(input);
    assertThat(encrypted).doesNotContain(input).isNotEqualTo(cipher.encrypt(input));
    assertThat(cipher.decrypt(encrypted)).isEqualTo(input);
    var other = new EmailPayloadCipher("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
    assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new EmailPayloadCipher(""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
