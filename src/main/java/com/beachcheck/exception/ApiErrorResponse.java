package com.beachcheck.exception;

import java.util.Map;
/**
 * Why: 오류 응답 형식을 고정해 클라이언트의 처리 일관성을 보장하기 위해.
 * Policy: 오류는 code/message/details 3요소로만 반환한다.
 * Contract(Input): code는 오류 분류를 나타낸다.
 * Contract(Output): 응답에는 code, message, details만 포함된다.
 */
public record ApiErrorResponse(
        String code,
        String message,
        Map<String, Object> details
) {
}
