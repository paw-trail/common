package com.pawtrail.common.config;

import com.pawtrail.common.security.filter.HeaderAuthenticationFilter;
import com.pawtrail.common.security.handler.CustomSecurityExceptionHandler;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.json.JsonMapper;

// Gateway가 주입한 헤더를 읽어 인증 객체를 심는 공통 보안 체인
// 실질적인 경로 방어는 Gateway와 보안그룹이 담당하고, 여기서 authenticated()를 기본값으로
// 두는 목적은 방어가 아니라 불변조건 확보
// 전부 permitAll로 열면 principal이 null인 채 컨트롤러에 들어와 매번 null 체크가 필요해짐
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CustomSecurityExceptionHandler customSecurityExceptionHandler(JsonMapper jsonMapper, Tracer tracer) {
        return new CustomSecurityExceptionHandler(jsonMapper, tracer);
    }

    @Bean
    // auth 서비스 등에서 별도 SecurityFilterChain Bean을 정의하면 서비스 측을 우선 적용
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CustomSecurityExceptionHandler customSecurityExceptionHandler) throws Exception {
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
