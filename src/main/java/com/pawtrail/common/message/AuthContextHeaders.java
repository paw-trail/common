package com.pawtrail.common.message;

// 인증 컨텍스트를 실어 나르는 헤더 키
// Gateway가 넣어주는 HTTP 헤더 이름이며, 서비스 간 호출과 Kafka에서도 같은 이름을 씀
// 세 곳이 같은 문자열을 각자 들고 있으면 어긋나도 알 수 없으므로 여기를 단일 출처로 둠
public final class AuthContextHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";

    private AuthContextHeaders() {
    }
}
