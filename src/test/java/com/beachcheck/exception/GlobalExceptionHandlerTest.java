package com.beachcheck.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.db.DBConstraints;
import jakarta.persistence.EntityNotFoundException;
import java.lang.reflect.Method;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

@DisplayName("GlobalExceptionHandler의 ProblemDetail 계약 테스트")
class GlobalExceptionHandlerTest {

  private static final MethodParameter VALIDATION_REQUEST_PARAMETER = validationRequestParameter();

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Nested
  @DisplayName("ApiException 처리")
  class ApiExceptionHandling {

    @Test
    @DisplayName("TC-EX-01: ApiException의 상태값과 오류 정보를 그대로 유지한다")
    void tcEx01_preserveApiExceptionContract() {
      // Given
      ApiException ex =
          new ApiException(
              ErrorCode.RESERVATION_DUPLICATE,
              "duplicate reservation",
              Map.of("beachId", "beach-1"));

      // When
      ProblemDetail problemDetail = handler.handleApiException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(409);
      assertThat(problemDetail.getTitle()).isEqualTo("Reservation already exists");
      assertThat(problemDetail.getDetail()).isEqualTo("duplicate reservation");
      assertThat(problemDetail.getProperties())
          .containsEntry("code", "RESERVATION_DUPLICATE")
          .containsEntry("details", Map.of("beachId", "beach-1"));
    }

    @Test
    @DisplayName("TC-EX-13: details가 null이면 빈 맵으로 정규화한다")
    void tcEx13_normalizeNullDetailsToEmptyMap() {
      // Given
      ApiException ex = new ApiException(ErrorCode.INVALID_REQUEST, "invalid request", null);

      // When
      ProblemDetail problemDetail = handler.handleApiException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getTitle()).isEqualTo("Invalid request");
      assertThat(problemDetail.getDetail()).isEqualTo("invalid request");
      assertThat(problemDetail.getProperties()).containsEntry("code", "INVALID_REQUEST");
      assertThat(problemDetail.getProperties()).containsKey("details");
      assertThat(problemDetail.getProperties().get("details")).isEqualTo(Map.of());
    }
  }

  @Nested
  @DisplayName("검증 예외 처리")
  class ValidationHandling {

    @Test
    @DisplayName("TC-EX-02: 검증 오류를 ProblemDetail로 매핑한다")
    void tcEx02_mapValidationErrorsToProblemDetail() {
      // Given
      MethodArgumentNotValidException ex =
          createValidationExceptionFromFieldErrors(
              new FieldError("request", "email", "must not be blank"),
              new FieldError("request", "password", "size must be between 8 and 20"));

      // When
      ProblemDetail problemDetail = handler.handleValidationException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getTitle()).isEqualTo("Validation Error");
      assertThat(problemDetail.getDetail())
          .isEqualTo("Validation failed. Please check the request fields.");
      assertThat(problemDetail.getProperties()).containsKey("errors");
      @SuppressWarnings("unchecked")
      Map<String, String> errors =
          (Map<String, String>) problemDetail.getProperties().get("errors");
      assertThat(errors)
          .containsEntry("email", "must not be blank")
          .containsEntry("password", "size must be between 8 and 20");
    }
  }

  @Nested
  @DisplayName("데이터 무결성 예외 처리")
  class DataIntegrityHandling {

    @Test
    @DisplayName("TC-EX-03: 중첩된 uk_user_beach 제약을 중복 찜 오류로 매핑한다")
    void tcEx03_mapNestedUkUserBeachConstraint() {
      // Given
      DataIntegrityViolationException ex = withNestedConstraint(DBConstraints.UK_USER_BEACH);

      // When
      ProblemDetail problemDetail = handler.handleDataIntegrityViolationException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(409);
      assertThat(problemDetail.getTitle()).isEqualTo("Duplicate Favorite");
      assertThat(problemDetail.getDetail()).isEqualTo("이미 찜한 해수욕장입니다.");
      assertThat(problemDetail.getProperties())
          .containsEntry("constraintName", DBConstraints.UK_USER_BEACH);
    }

    @Test
    @DisplayName("TC-EX-04: 메시지에만 uk_user_beach가 있어도 중복 찜 오류로 매핑한다")
    void tcEx04_mapUkUserBeachMessageOnlyFallback() {
      // Given
      DataIntegrityViolationException ex =
          withMessageOnly("duplicate key value violates unique constraint uk_user_beach");

      // When
      ProblemDetail problemDetail = handler.handleDataIntegrityViolationException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(409);
      assertThat(problemDetail.getTitle()).isEqualTo("Duplicate Favorite");
      assertThat(problemDetail.getDetail()).isEqualTo("이미 찜한 해수욕장입니다.");
      assertThat(problemDetail.getProperties())
          .containsEntry("constraintName", DBConstraints.UK_USER_BEACH);
    }

    @Test
    @DisplayName("TC-EX-05: 예약 중복 unique 제약을 예약 중복 오류 계약으로 매핑한다")
    void tcEx05_mapReservationUniqueConstraint() {
      // Given
      DataIntegrityViolationException ex =
          withConstraintName(DBConstraints.UK_RESERVATION_USER_BEACH_TIME);

      // When
      ProblemDetail problemDetail = handler.handleDataIntegrityViolationException(ex);

      // Then
      assertReservationDuplicate(problemDetail);
    }

    @Test
    @DisplayName("TC-EX-06: 메시지에만 예약 unique 제약이 있어도 예약 중복 오류로 매핑한다")
    void tcEx06_mapReservationUniqueMessageOnlyFallback() {
      // Given
      DataIntegrityViolationException ex =
          withMessageOnly(
              "duplicate key value violates unique constraint uk_reservation_user_beach_time");

      // When
      ProblemDetail problemDetail = handler.handleDataIntegrityViolationException(ex);

      // Then
      assertReservationDuplicate(problemDetail);
    }

    @Test
    @DisplayName("TC-EX-07: 알 수 없는 데이터 무결성 예외를 일반 잘못된 요청으로 매핑한다")
    void tcEx07_mapUnknownDataIntegrityException() {
      // Given
      DataIntegrityViolationException ex = new DataIntegrityViolationException("generic violation");

      // When
      ProblemDetail problemDetail = handler.handleDataIntegrityViolationException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getTitle()).isEqualTo("Data Integrity Violation");
      assertThat(problemDetail.getDetail()).isEqualTo("데이터 무결성 제약 위반입니다.");
    }
  }

  @Nested
  @DisplayName("기타 예외 처리")
  class FallbackHandling {

    @Test
    @DisplayName("TC-EX-08: IllegalArgumentException을 잘못된 요청으로 매핑한다")
    void tcEx08_mapIllegalArgumentException() {
      // Given
      IllegalArgumentException ex = new IllegalArgumentException("invalid request");

      // When
      ProblemDetail problemDetail = handler.handleIllegalArgumentException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getTitle()).isEqualTo("Invalid Request");
      assertThat(problemDetail.getDetail()).isEqualTo("invalid request");
    }

    @Test
    @DisplayName("TC-EX-09: BadCredentialsException을 인증 실패로 매핑한다")
    void tcEx09_mapBadCredentialsException() {
      // Given
      BadCredentialsException ex = new BadCredentialsException("bad credentials");

      // When
      ProblemDetail problemDetail = handler.handleBadCredentialsException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(401);
      assertThat(problemDetail.getTitle()).isEqualTo("Authentication Failed");
      assertThat(problemDetail.getDetail()).isEqualTo("bad credentials");
    }

    @Test
    @DisplayName("TC-EX-10: EntityNotFoundException을 리소스 없음으로 매핑한다")
    void tcEx10_mapEntityNotFoundException() {
      // Given
      EntityNotFoundException ex = new EntityNotFoundException("resource not found");

      // When
      ProblemDetail problemDetail = handler.handleEntityNotFoundException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(404);
      assertThat(problemDetail.getTitle()).isEqualTo("Resource Not Found");
      assertThat(problemDetail.getDetail()).isEqualTo("resource not found");
    }

    @Test
    @DisplayName("TC-EX-11: IllegalStateException을 충돌 상태로 매핑한다")
    void tcEx11_mapIllegalStateException() {
      // Given
      IllegalStateException ex = new IllegalStateException("invalid state");

      // When
      ProblemDetail problemDetail = handler.handleIllegalStateException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(409);
      assertThat(problemDetail.getTitle()).isEqualTo("Invalid State");
      assertThat(problemDetail.getDetail()).isEqualTo("invalid state");
    }

    @Test
    @DisplayName("TC-EX-12: 일반 예외를 내부 서버 오류로 매핑한다")
    void tcEx12_mapGeneralException() {
      // Given
      RuntimeException ex = new RuntimeException("boom");

      // When
      ProblemDetail problemDetail = handler.handleGeneralException(ex);

      // Then
      assertThat(problemDetail.getStatus()).isEqualTo(500);
      assertThat(problemDetail.getTitle()).isEqualTo("Internal Server Error");
      assertThat(problemDetail.getDetail())
          .isEqualTo("An unexpected error occurred. Please try again later.");
      assertThat(problemDetail.getProperties())
          .containsEntry("errorType", "RuntimeException")
          .containsEntry("message", "boom");
    }
  }

  /**
   * Spring의 MethodArgumentNotValidException은 MethodParameter가 필요하므로, 테스트에서는 전용 더미 메서드의 파라미터를 한 번만
   * 구성해 재사용한다.
   */
  private MethodArgumentNotValidException createValidationExceptionFromFieldErrors(
      FieldError... fieldErrors) {
    ValidationRequest request = new ValidationRequest("", "");
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
    for (FieldError fieldError : fieldErrors) {
      bindingResult.addError(fieldError);
    }
    return new MethodArgumentNotValidException(VALIDATION_REQUEST_PARAMETER, bindingResult);
  }

  private DataIntegrityViolationException withConstraintName(String constraintName) {
    return new DataIntegrityViolationException(
        "constraint violation",
        new ConstraintViolationException("constraint violation", null, constraintName));
  }

  private DataIntegrityViolationException withMessageOnly(String message) {
    return new DataIntegrityViolationException(message);
  }

  private DataIntegrityViolationException withNestedConstraint(String constraintName) {
    return new DataIntegrityViolationException(
        "constraint violation",
        new RuntimeException(
            new ConstraintViolationException("constraint violation", null, constraintName)));
  }

  private void assertReservationDuplicate(ProblemDetail problemDetail) {
    assertThat(problemDetail.getStatus()).isEqualTo(409);
    assertThat(problemDetail.getTitle()).isEqualTo("Reservation already exists");
    assertThat(problemDetail.getDetail()).isEqualTo("Reservation already exists");
    assertThat(problemDetail.getProperties())
        .containsEntry("code", "RESERVATION_DUPLICATE")
        .containsEntry("constraintName", DBConstraints.UK_RESERVATION_USER_BEACH_TIME);
    assertThat(problemDetail.getProperties()).containsKey("details");
    assertThat(problemDetail.getProperties().get("details")).isNull();
  }

  private record ValidationRequest(String email, String password) {}

  private static MethodParameter validationRequestParameter() {
    try {
      Method method =
          ValidationController.class.getDeclaredMethod("handle", ValidationRequest.class);
      return new MethodParameter(method, 0);
    } catch (NoSuchMethodException ex) {
      throw new IllegalStateException("Failed to resolve validation request parameter", ex);
    }
  }

  private static final class ValidationController {
    @SuppressWarnings("unused")
    private void handle(ValidationRequest request) {}
  }
}
