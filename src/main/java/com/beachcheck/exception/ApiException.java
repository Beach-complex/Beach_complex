package com.beachcheck.exception;

import java.util.Collections;
import java.util.Map;

/**
 * Why: 오류 코드를 예외에 결합해 일관된 에러 처리를 보장하기 위해.
 * Policy: details는 null을 허용하지 않고 빈 맵으로 정규화한다.
 * Contract(Input): details가 null일 수 있다.
 * Contract(Output): getDetails()는 null이 아니다.
 */

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = Collections.emptyMap();
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = Collections.emptyMap();
    }

    public ApiException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        // null details는 빈 맵으로 정규화한다.
        this.details = (details == null) ? Collections.emptyMap() : details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
