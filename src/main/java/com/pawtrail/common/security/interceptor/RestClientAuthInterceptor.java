package com.pawtrail.common.security.interceptor;

import com.pawtrail.common.message.AuthContextHeaders;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

// 서비스가 다른 서비스를 호출할 때 현재 사용자 정보를 헤더로 실어 보냄
// 이것이 없으면 호출받은 쪽의 HeaderAuthenticationFilter가 심을 값이 없어
// 그쪽에서 만든 엔티티의 createdBy가 전부 SYSTEM 폴백으로 남음
//
// 배치나 스케줄러가 호출하는 경우에는 SecurityContext가 비어 있어 헤더 없이 나감
// 받는 쪽도 헤더가 없으면 그냥 통과시키므로 문제되지 않음
//
// traceparent는 다루지 않음, Micrometer가 자동으로 처리하며
// 직접 넣으면 헤더가 중복돼 표준상 무효 처리되어 받는 쪽이 새 trace를 시작함
//
// CommonRestClientAutoConfiguration 의 internalRestClientBuilder 에 붙어 있음
// 그 빌더로 만든 RestClient 로 나가는 요청에만 실림
//
// 바깥 API 를 부르는 externalRestClientBuilder 에는 붙이지 않음
// 우리 사용자 식별자를 바깥에 보낼 이유가 없음
public class RestClientAuthInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 인증된 사용자 요청에서 시작된 호출일 때만 헤더를 붙임
        if (authentication != null
                && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {

            request.getHeaders().add(AuthContextHeaders.USER_ID, principal.accountId().toString());
            request.getHeaders().add(AuthContextHeaders.USER_ROLE, principal.role().name());
        }

        return execution.execute(request, body);
    }
}
