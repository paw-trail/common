package com.pawtrail.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 서비스 공통 에러 코드 계약.
 *
 * <p>각 서비스는 자신의 도메인 에러를 이 인터페이스를 구현한 enum으로 정의한다.
 * 공통 코드는 {@link CommonErrorCode}에 있으며, {@link CustomException}이
 * 두 종류를 구분 없이 받는다.
 *
 * <p><b>구현 규칙</b>
 * <ul>
 *   <li>{@code getCode()}는 반드시 {@code name()}을 그대로 반환한다.
 *       접두사를 붙이거나 별도 문자열을 쓰지 않는다. 상수 이름이 곧 응답
 *       {@code code} 값이자 API 계약이므로, 규칙을 어겨도 컴파일러가 잡지 못한다.</li>
 *   <li>도메인 개념({@code PLACE_NOT_FOUND} 등)은 {@code CommonErrorCode}가 아니라
 *       각 서비스의 {@code <도메인>ErrorCode}에 정의한다. 공통에 두면 코드 하나를
 *       추가할 때마다 공통 모듈 재배포와 전 서비스 버전업이 필요해진다.</li>
 *   <li>메시지는 고정 문자열이다. 동적 값이 필요하면 응답 {@code data}에 담는다.</li>
 * </ul>
 *
 * <p>세 값은 응답의 서로 다른 층으로 나간다.
 *
 * <pre>
 * HTTP/1.1 404 Not Found            &lt;- getHttpStatus()
 * Content-Type: application/json      (게이트웨이·모니터링·RestClient가 읽는 층)
 *
 * {
 *   "code": "PLACE_NOT_FOUND",      &lt;- getCode()
 *   "message": "장소를 찾을 수 없습니다",  &lt;- getMessage()
 *   "data": null,
 *   "traceId": "a1b2c3..."            (TraceIdResponseAdvice가 채움)
 * }
 * </pre>
 */

public interface ErrorCode {
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
}
