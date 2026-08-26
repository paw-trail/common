package com.pawtrail.common.audit;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuditorProvider implements AuditorAware<String> {

    // application.yml에 있는 이름을 가져옴
    @Value("${app.auditor.system-name:SYSTEM}")
    private String systemName;

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of(systemName);
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of(systemName);
        }

        return Optional.of(authentication.getName());
    }
}
