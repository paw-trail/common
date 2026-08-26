package com.pawtrail.common.message;

// Kafka 헤더로 실어 나르는 인증 컨텍스트 키
// HTTP의 X-User-Id·X-User-Role과 같은 이름을 써서 어디서 왔는지가 드러나게 함
public final class AuthContextHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";

    private AuthContextHeaders() {
    }
}
