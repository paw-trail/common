package com.pawtrail.common.audit;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuditorProvider implements AuditorAware<String> {

    // application.yml에 있는 이름을 가져옴
    @Value("${app.auditor.system-name:SYSTEM}")
    private String systemName;

    // 현재 감사자 이름, soft delete처럼 코드가 직접 값을 넣는 자리에서 호출
    public String current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 인증 정보가 없거나 인증 안된 상태(배치, 스케줄러)
        if (authentication == null || !authentication.isAuthenticated()) {
            return systemName;
        }

        // Spring Security 기본 익명 사용자
        if (authentication instanceof AnonymousAuthenticationToken) {
            return systemName;
        }

        // 정상 인증된 사용자(CustomUserPrincipal의 getName() = accountId)
        return authentication.getName();
    }

    // JPA Auditing이 @CreatedBy·@LastModifiedBy를 채울 때 자동 호출
    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(current());
    }
}
