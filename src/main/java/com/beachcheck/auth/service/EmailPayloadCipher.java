package com.beachcheck.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 인증 링크 원문을 DB에 노출하지 않는다. 키는 모든 워커에서 동일하게 유지한다. */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class EmailPayloadCipher {
  private final byte[] key;
  private final SecureRandom random = new SecureRandom();

  public EmailPayloadCipher(@Value("${app.email.jobs.payload-key:}") String encodedKey) {
    if (encodedKey.isBlank()) {
      throw new IllegalArgumentException("APP_EMAIL_JOBS_PAYLOAD_KEY is required for email jobs");
    }
    key = Base64.getDecoder().decode(encodedKey);
    if (key.length != 32) {
      throw new IllegalArgumentException("Email payload key must contain 32 bytes");
    }
  }

  public String encrypt(String text) {
    byte[] iv = new byte[12];
    random.nextBytes(iv);
    return Base64.getEncoder().encodeToString(iv)
        + ":"
        + Base64.getEncoder()
            .encodeToString(crypt(Cipher.ENCRYPT_MODE, iv, text.getBytes(StandardCharsets.UTF_8)));
  }

  public String decrypt(String payload) {
    String[] parts = payload.split(":", 2);
    return new String(
        crypt(
            Cipher.DECRYPT_MODE,
            Base64.getDecoder().decode(parts[0]),
            Base64.getDecoder().decode(parts[1])),
        StandardCharsets.UTF_8);
  }

  private byte[] crypt(int mode, byte[] iv, byte[] input) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      return cipher.doFinal(input);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Email payload encryption failed");
    }
  }
}
