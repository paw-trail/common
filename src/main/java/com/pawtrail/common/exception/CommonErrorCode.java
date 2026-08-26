package com.pawtrail.common.exception;

import org.springframework.http.HttpStatus;

// 모든 서버에서 공통으로 활용될 수 있는 에러코드들, ErrorCode 규칙을 구현
public enum CommonErrorCode implements ErrorCode {

    // 검증 실패
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "올바르지 않은 입력값 입니다."),
    // 인증 실패
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "인증에 실패하였습니다."),
    // 접근 권한 없음
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    // 내부 서버 에러
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부에 에러가 발생하였습니다."),
    // 외부 API 호출 에러
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패하였습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    CommonErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }

    @Override
    public String getCode() {
        return this.name();
    }

    @Override
    public String getMessage() {
        return this.message;
    }

}
