package com.pawtrail.common.security.filter;

import com.pawtrail.common.enums.Role;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");
        String roleHeader = request.getHeader("X-User-Role");

        // 헤더가 하나라도 없으면 바로 filterChain.doFilter() 호출 후 return
        // 인증이 필요한 경로인지 판단하는 것은 SecurityConfig의 몫
        if (userIdHeader == null || roleHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // userIdHeader -> UUID 변환
            UUID userId = UUID.fromString(userIdHeader);
            // roleHeader -> Role enum 변환
            Role userRole = Role.valueOf(roleHeader);

            // CustomUserPrincipal 생성
            CustomUserPrincipal customUserPrincipal = new CustomUserPrincipal(userId, userRole);

            // 권한 객체 생성
            SimpleGrantedAuthority simpleGrantedAuthority = new SimpleGrantedAuthority("ROLE_" + userRole.name());

            // 토큰 생성
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    customUserPrincipal,
                    null,
                    List.of(simpleGrantedAuthority)
            );

            // 새 컨텍스트를 만들어 심기
            // getContext()를 직접 변형하지 않는 이유는 그것이 지연 로딩된 공용 객체일 수 있기 때문임
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

        } catch (IllegalArgumentException e) {
            log.warn("유효하지 않은 인증 헤더 형식입니다. userIdHeader: {}, roleHeader: {}", userIdHeader, roleHeader);
        }

        // (정상/비정상) 상관없이 다음 필터로 진행
        filterChain.doFilter(request, response);
    }
}
