package com.beachcheck.exception;

import org.springframework.http.HttpStatus;

/**
 * Why: 오류의 HTTP 매핑을 중앙화해 처리 일관성을 보장하기 위해. Policy: 각 오류는 고정된 status와 code와 기본 메시지를 가진다.
 * Contract(Input): ErrorCode 상수를 사용한다. Contract(Output): status와 code와 defaultMessage는 상수 값이다.
 */
public enum ErrorCode {
  RESERVATION_PAST_TIME(
      HttpStatus.BAD_REQUEST, "RESERVATION_PAST_TIME", "Reservation time must be >= now(UTC)"),
  RESERVATION_INVALID_TIME(
      HttpStatus.BAD_REQUEST, "RESERVATION_INVALID_TIME", "Invalid reservedAtUtc format"),
  RESERVATION_DUPLICATE(HttpStatus.CONFLICT, "RESERVATION_DUPLICATE", "Reservation already exists"),
  RESERVATION_FORBIDDEN(HttpStatus.FORBIDDEN, "RESERVATION_FORBIDDEN", "Not allowed"),
  RESERVATION_INTERNAL_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "RESERVATION_INTERNAL_ERROR", "Reservation failed"),

  BEACH_NOT_FOUND(HttpStatus.NOT_FOUND, "BEACH_NOT_FOUND", "Beach not found"),

  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"),
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed"),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request"),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found"),
  INVALID_STATE(HttpStatus.CONFLICT, "INVALID_STATE", "Invalid state"),
  INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Unexpected error");

  private final HttpStatus status;
  private final String code;
  private final String defaultMessage;

  ErrorCode(HttpStatus status, String code, String defaultMessage) {
    this.status = status;
    this.code = code;
    this.defaultMessage = defaultMessage;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public String getDefaultMessage() {
    return defaultMessage;
  }
}
