package com.pawtrail.common.security.principal;

import com.pawtrail.common.enums.Role;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

public record CustomUserPrincipal(UUID accountId, Role role) implements AuthenticatedPrincipal {
    @Override
    public String getName() {
        return this.accountId.toString();
    }
}
