package com.pawtrail.common.message;

// 인증 컨텍스트를 실어 나르는 헤더 키
// Gateway가 넣어주는 HTTP 헤더 이름이며, 서비스 간 호출과 Kafka에서도 같은 이름을 씀
//
// * 이 상수를 참조할 수 있는 것은 공통 모듈을 쓰는 도메인 서비스뿐임
//   게이트웨이는 공통 모듈을 의존하지 않아 같은 문자열을 자기 저장소에 따로 적고 있음
//   즉 문자열이 두 저장소에 각각 존재하며 여기가 완전한 단일 출처는 아님
//
// * 어긋나면 도메인 서비스가 401 을 냄
//   게이트웨이가 다른 이름으로 넣으면 필터가 헤더를 못 찾아 인증 없이 통과시키고
//   그 뒤 경로 규칙에서 막히는 형태라 원인이 게이트웨이 문자열이라는 것이 안 드러남
//   토픽 이름을 공통 모듈에 두지 않은 것과 같은 성격이며 문서를 단일 참조로 삼음
public final class AuthContextHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";

    private AuthContextHeaders() {
    }
}
