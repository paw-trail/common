package com.pawtrail.common.response;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class TraceIdResponseAdvice implements ResponseBodyAdvice<Object> {

    private final Tracer tracer;

    // 선언된 반환 타입만으로는 판별할 수 없음
    // GlobalExceptionHandler가 ResponseEntity<CommonApiResponse<T>>를 반환하므로
    // 여기서 CommonApiResponse를 검사하면 실패 응답이 전부 걸러짐
    // 실제 판별은 beforeBodyWrite의 instanceof가 담당
    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {

        // CommonApiResponse 형태가 아니면 그대로 반환
        if (!(body instanceof CommonApiResponse<?> apiResponse)) {
            return body;
        }

        // Span을 조회해 지역 변수로 사용
        Span currentSpan = tracer.currentSpan();

        // 추적이 꺼져 있거나 현재 요청이 스팬 밖이면 currentSpan이 null임
        // 아래 조건 검사를 생략하면 traceId를 기록할 수 없는 조건(모니터링 툴을 내린 경우)에서
        // context()를 호출할 때 NullPointerException이 발생해 모든 API가 500이 됨
        if (currentSpan == null) {
            return body;
        }

        return apiResponse.withTraceId(currentSpan.context().traceId());
    }
}
