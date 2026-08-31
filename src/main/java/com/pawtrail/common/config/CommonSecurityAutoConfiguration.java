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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
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

    // 스프링 부트가 만드는 기본 사용자 계정을 물러나게 함
    //
    // * 왜 필요한가
    //   SecurityFilterChain 을 정의하면 기본 보안 체인은 물러나지만
    //   사용자 계정을 만드는 자동 설정은 그것과 별개라 그대로 뜸
    //   그래서 기동할 때마다 아래 로그가 남음
    //     Using generated security password: 65e31c6c-...
    //   비밀번호가 로그에 찍히고 그 로그가 Loki 로 넘어감
    //   AuthenticationManager 빈이 있으면 그 자동 설정이 물러남
    //
    // * 왜 항상 예외를 던지는가
    //   우리는 스프링의 인증 흐름을 쓰지 않음
    //   아이디와 비밀번호를 스프링이 확인하는 것이 아니라
    //   인증 서비스가 직접 확인해 토큰을 만들고,
    //   나머지 서비스는 게이트웨이가 넣어 준 헤더만 믿음
    //   그러므로 이 빈이 불리는 상황 자체가 잘못된 것이며
    //   조용히 실패하는 것보다 무엇이 잘못됐는지 말하고 끝내는 편이 나음
    //
    // * ProviderManager 를 쓰지 않는 이유
    //   그 클래스는 provider 목록이 비어 있고 부모도 없으면
    //   생성자에서 예외를 던져 기동 자체가 실패함
    //   AuthenticationManager 는 메서드가 하나뿐이라 람다로 둘 수 있음
    @Bean
    @ConditionalOnMissingBean
    public AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new AuthenticationServiceException(
                    "이 서비스는 스프링 인증 흐름을 쓰지 않습니다. "
                            + "인증은 게이트웨이가 주입한 헤더로 이루어지며 "
                            + "로그인은 인증 서비스가 직접 처리합니다");
        };
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

                        // 관리자 API는 ADMIN 역할만 접근할 수 있음
                        // 관리자 기능이 여러 서비스에 흩어져 있어 각 서비스가 따로 막게 하면
                        // 하나만 빠뜨려도 그 서비스가 그대로 열리므로 공통 모듈에서 한 번에 막음
                        // HeaderAuthenticationFilter가 "ROLE_" 접두사를 붙여 권한을 만들므로
                        // hasRole 이 그대로 동작함
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // 그 외 모든 요청은 인증(헤더를 통한 principal 주입)이 필수
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
