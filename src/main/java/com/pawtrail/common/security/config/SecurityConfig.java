package com.pawtrail.common.security.config;

import com.pawtrail.common.security.filter.HeaderAuthenticationFilter;
import com.pawtrail.common.security.handler.CustomSecurityExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomSecurityExceptionHandler customSecurityExceptionHandler;

    @Bean
    // auth 서비스 등에서 별도 SecurityFilterChain Bean을 정의하면 서비스 측을 우선 적용
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 브라우저 폼 로그인 및 세션 기반이 아니므로 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 세션을 생성하지 않음 (STATELESS)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 401(인증 실패) 및 403(인가 실패) 공통 예외 핸들러 연결
                .exceptionHandling(exception -> exception
                        // 401 에러 처리
                        .authenticationEntryPoint(customSecurityExceptionHandler)
                        // 403 에러 처리
                        .accessDeniedHandler(customSecurityExceptionHandler)
                )

                // Gateway가 전달한 헤더를 읽어 SecurityContext에 인증 객체를 심는 필터 등록
                // UsernamePasswordAuthenticationFilter가 실행되기 전에 먼저 동작하도록 배치
                .addFilterBefore(new HeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)

                // 경로별 접근 규칙 설정
                .authorizeHttpRequests(auth -> auth
                        // 내부 통신(/internal/**)과 헬스체크(/actuator/**)는 인증 없이 허용
                        .requestMatchers("/internal/**", "/actuator/**").permitAll()
                        // 그 외 모든 요청은 인증(헤더를 통한 principal 주입)이 필수
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
