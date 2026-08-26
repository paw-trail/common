package com.pawtrail.common.config;

import com.pawtrail.common.exception.handler.GlobalExceptionHandler;
import com.pawtrail.common.response.TraceIdResponseAdvice;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 서블릿 웹 서비스의 공통 응답·예외 처리 Bean 등록
 *
 * ResponseBodyAdvice는 spring-webmvc에만 있으므로 WebFlux인 Gateway에서 로딩되면 안 됨
 * ConditionalOnWebApplication(SERVLET)과 ConditionalOnClass가 역할을 하며,
 * 덕분에 Gateway가 common을 의존해도 ErrorCode·CommonApiResponse만 안전하게 사용 가능
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice")
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    // 추적이 꺼진 환경에서는 Tracer 자체가 없으므로 클래스 유무로 한 번 더 거름
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    public TraceIdResponseAdvice traceIdResponseAdvice(Tracer tracer) {
        return new TraceIdResponseAdvice(tracer);
    }
}
