package com.pawtrail.common.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * 만드는 길과 읽는 길을 겸합니다.
     *
     * private 을 그대로 둡니다.
     * Jackson 은 리플렉션으로 부르므로 접근 제한자를 보지 않고,
     * 밖에서는 아래 정적 메서드로만 만들게 한 의도가 유지됩니다.
     *
     * @JsonProperty 를 파라미터마다 붙여야 합니다.
     * 자바는 컴파일 옵션 없이는 생성자 파라미터 이름을 클래스 파일에 남기지 않아
     * 이름이 없으면 Jackson 이 어느 칸에 무엇을 넣을지 알 수 없습니다.
     *
     * 이것이 필요해진 것은 서비스가 서비스를 부르기 시작하면서입니다.
     * 보내는 쪽은 이 클래스로 응답을 만들고 받는 쪽은 그것을 다시 이 타입으로 읽는데,
     * 만드는 길만 있고 읽는 길이 없어 받는 쪽이 InvalidDefinitionException 으로 실패했습니다.
     * user 가 review 를 부르는 자리에서 처음 드러났습니다.
     */
    @JsonCreator
    private CommonApiResponse(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("data") T data,
            @JsonProperty("traceId") String traceId) {
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
