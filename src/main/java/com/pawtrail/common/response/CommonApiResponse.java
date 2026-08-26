package com.pawtrail.common.response;

import com.pawtrail.common.exception.ErrorCode;
import lombok.Getter;

@Getter
public class CommonApiResponse<T> {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String SUCCESS_MESSAGE = "요청이 성공적으로 처리되었습니다.";

    private final String code;
    private final String message;
    private final T data;
    private final String traceId;

    private CommonApiResponse(String code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    // 성공 시 공통 응답
    public static <T> CommonApiResponse<T> success(T data) {
        return new CommonApiResponse<T>(SUCCESS_CODE, SUCCESS_MESSAGE, data, null);
    }

    // 실패 시 공통 응답 (data 없는 경우)
    public static <T> CommonApiResponse<T> error(ErrorCode errorCode) {
        return new CommonApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null, null);
    }

    // 실패 시 공통 응답 (data 있는 경우)
    public static <T> CommonApiResponse<T> error(ErrorCode errorCode, T data) {
        return new CommonApiResponse<T>(errorCode.getCode(), errorCode.getMessage(), data, null);
    }

    // TraceId 주입용
    public CommonApiResponse<T> withTraceId(String traceId) {
        return new CommonApiResponse<>(this.code, this.message, this.data, traceId);
    }
}
