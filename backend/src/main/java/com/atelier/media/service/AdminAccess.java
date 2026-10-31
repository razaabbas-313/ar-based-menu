package com.atelier.media.service;

import com.atelier.media.exception.InvalidTokenException;
import com.atelier.media.model.PlatformUser;
import com.atelier.media.repository.PlatformUserRepository;
import com.atelier.media.security.AdminPrincipal;
import org.springframework.stereotype.Component;

/** Revalidates the current platform account on every admin operation. */
@Component
public class AdminAccess {

    private final PlatformUserRepository users;

    public AdminAccess(PlatformUserRepository users) {
        this.users = users;
    }

    public PlatformUser requireAccountForUpdate(AdminPrincipal principal) {
        users.findByIdForUpdate(principal.adminId()).orElseThrow(() -> new InvalidTokenException("This admin account no longer exists."));
        return requireAccount(principal);
    }
    public PlatformUser requireAccount(AdminPrincipal principal) {
        PlatformUser admin = users.findById(principal.adminId())
                .orElseThrow(() -> new InvalidTokenException("This admin account no longer exists. Please sign in again."));
        if (principal.tokenVersion() == null || admin.getTokenVersion() != principal.tokenVersion()) {
            throw new InvalidTokenException("This admin session has been revoked. Please sign in again.");
        }
        if (!PlatformUser.ROLE_SUPER_ADMIN.equals(admin.getRole()) || !admin.hasUsablePassword()) {
            throw new InvalidTokenException("This admin account is not available. Please sign in again.");
        }
        return admin;
    }
}
