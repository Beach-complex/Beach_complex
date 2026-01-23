package com.beachcheck.exception;

import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.beachcheck.db.DBConstraints;
/**
 * Why: 예외를 ProblemDetail로 매핑해 오류 응답의 일관성을 보장하기 위해.
 *
 * <p>Policy: 예외 유형별로 고정된 HttpStatus와 속성 키를 사용한다.
 *
 * <p>Contract(Input): 예외가 핸들러 메서드로 전달된다.
 *
 * <p>Contract(Output): ProblemDetail에 status와 필요한 속성을 담아 반환한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ProblemDetail handleApiException(ApiException ex) {
    ErrorCode code = ex.getErrorCode();
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.getStatus(), ex.getMessage());
    pd.setTitle(code.getCode());
    // ApiException 응답에서 code/details 키는 클라이언트 계약이므로 변경에 주의한다.
    pd.setProperty("code", code.getCode());
    pd.setProperty("details", ex.getDetails());
    return pd;
  }

  /** Validation 에러 처리 (회원가입 양식 오류 등) 각 필드별로 어떤 문제가 있는지 명확하게 반환 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Validation failed. Please check the request fields.");
    problemDetail.setTitle("Validation Error");

    // 각 필드별 에러 메시지를 담을 맵
    Map<String, String> errors = new HashMap<>();

    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.put(error.getField(), error.getDefaultMessage());
    }

    problemDetail.setProperty("errors", errors);
    return problemDetail;
  }

  /** 중복 이메일, 잘못된 입력값 등 IllegalArgumentException 처리 */
  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problemDetail.setTitle("Invalid Request");
    return problemDetail;
  }

  /** 로그인 실패, 인증 실패 처리 */
  @ExceptionHandler(BadCredentialsException.class)
  public ProblemDetail handleBadCredentialsException(BadCredentialsException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    problemDetail.setTitle("Authentication Failed");
    return problemDetail;
  }

  /** 사용자, 토큰 등을 찾을 수 없을 때 처리 */
  @ExceptionHandler(EntityNotFoundException.class)
  public ProblemDetail handleEntityNotFoundException(EntityNotFoundException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problemDetail.setTitle("Resource Not Found");
    return problemDetail;
  }

  /** 계정 비활성화, 토큰 만료 등 상태 에러 처리 */
  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail handleIllegalStateException(IllegalStateException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problemDetail.setTitle("Invalid State");
    return problemDetail;
  }

  /**
   * DB 제약 위반 처리 (UNIQUE, FOREIGN KEY 등)
   *
   * <p>Why: 트랜잭션 커밋 시점에 발생하는 DB 제약 위반을 일관되게 처리
   *
   * <p>Policy: UNIQUE 제약 위반은 409 CONFLICT, 기타는 400 BAD_REQUEST
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
    String message = ex.getMessage();
    String constraintName = null;
    Throwable cause = ex.getCause();
    while (cause != null) {
      if (cause instanceof ConstraintViolationException constraintViolationException) {
        constraintName = constraintViolationException.getConstraintName();
        break;
      }
      cause = cause.getCause();
    }

    // UNIQUE 제약 위반 판별 (찜 중복 등)
    if (DBConstraints.UK_USER_BEACH.equals(constraintName)
        || (message != null && message.contains(DBConstraints.UK_USER_BEACH))) {
      ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "이미 찜한 해수욕장입니다.");
      pd.setTitle("Duplicate Favorite");
      pd.setProperty("constraintName", DBConstraints.UK_USER_BEACH);
      return pd;
    }

    if (DBConstraints.UK_RESERVATION_USER_BEACH_TIME.equals(constraintName)
        || (message != null && message.contains(DBConstraints.UK_RESERVATION_USER_BEACH_TIME))) {
      ProblemDetail pd =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.CONFLICT, ErrorCode.RESERVATION_DUPLICATE.getDefaultMessage());
      pd.setTitle(ErrorCode.RESERVATION_DUPLICATE.getCode());
      pd.setProperty("code", ErrorCode.RESERVATION_DUPLICATE.getCode());
      pd.setProperty("details", null);
      pd.setProperty("constraintName", DBConstraints.UK_RESERVATION_USER_BEACH_TIME);
      return pd;
    }

    // 기타 제약 위반
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "데이터 무결성 제약 위반입니다.");
    pd.setTitle("Data Integrity Violation");
    return pd;
  }

  /** 그 외 모든 예외 처리 (예상치 못한 에러) */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleGeneralException(Exception ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.");
    problemDetail.setTitle("Internal Server Error");
    problemDetail.setProperty("errorType", ex.getClass().getSimpleName());

    // 개발 환경에서 디버깅을 위해 스택 트레이스 포함 (프로덕션에서는 제거 권장)
    problemDetail.setProperty("message", ex.getMessage());

    return problemDetail;
  }
}
