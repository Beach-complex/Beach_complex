package com.beachcheck.global.util;

/**
 * Why: DTO toString 경로에서 민감 값과 값의 구조가 로그에 노출되지 않도록 공통 마스킹 표현을 제공한다.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>전체 마스킹은 필드 값과 필드 구조를 숨긴다.
 *   <li>부분 마스킹은 필드명만 남기고 값을 고정 마스킹 값으로 대체한다.
 * </ul>
 *
 * <p>Contract(Output): 모든 마스킹 값은 {@link #MASKED_VALUE}를 사용한다.
 */
public final class MaskingUtils {

  public static final String MASKED_VALUE = "****";

  private MaskingUtils() {}

  public static String maskedRecord(String typeName) {
    return typeName + "[" + MASKED_VALUE + "]";
  }

  public static String maskedField(String fieldName) {
    return fieldName + "=" + MASKED_VALUE;
  }
}
