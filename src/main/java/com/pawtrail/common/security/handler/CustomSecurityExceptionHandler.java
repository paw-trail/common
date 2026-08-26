package com.pawtrail.common.security.handler;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.response.CommonApiResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    // Spring Boot 4로 올라오면서 JsonMapper(=Jackson 3)를 권장함
    private final JsonMapper jsonMapper;
    private final Tracer tracer;

    // Case 1. 401 Unauthorized (인증실패)
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        log.warn("인증 실패 (401): {}", authException.getMessage());
        sendResponse(response, CommonErrorCode.AUTHENTICATION_FAILED);
    }

    // Case 2. 403 Forbidden (권한없음)
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        log.warn("접근 권한 없음 (403): {}", accessDeniedException.getMessage());
        sendResponse(response, CommonErrorCode.ACCESS_DENIED);
    }

    // JSON 응답 작성용
    private void sendResponse(HttpServletResponse response, CommonErrorCode errorCode) throws IOException {
        // HTTP 응답 상태 코드 및 헤더 설정
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // 한글 깨짐을 방지하고자 인코딩
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // 공통 응답 규격(CommonApiResponse) 객체 생성
        CommonApiResponse<?> apiResponse = CommonApiResponse.error(errorCode);

        // TraceId 주입 (추적이 꺼져 있거나 스팬 밖이면 currentSpan이 null)
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            apiResponse = apiResponse.withTraceId(currentSpan.context().traceId());
        }

        // JsonMapper를 사용하여 JSON 문자열로 직렬화 후 Body에 쓰기
        String jsonResponse = jsonMapper.writeValueAsString(apiResponse);
        response.getWriter().write(jsonResponse);
    }
}
