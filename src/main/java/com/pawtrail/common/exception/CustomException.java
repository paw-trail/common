package com.pawtrail.common.exception;

// 서비스에서 던질 유일한 예외, 공통 에러를 포함하여 ErrorCode를 통해 각 예외를 처리함
public class CustomException extends RuntimeException {
    private final ErrorCode errorCode;

    // 단순 비즈니스 로직 에러용
    public CustomException(ErrorCode errorCode) {
        super(errorCode.getCode() + ": " + errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // 원인 예외를 함께 전달
    public CustomException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getCode() + ": " + errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return this.errorCode;
    }
}
