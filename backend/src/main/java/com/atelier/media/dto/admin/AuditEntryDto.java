package com.atelier.media.dto.admin;

import com.atelier.media.model.AdminAuditLog;

import java.time.Instant;

public record AuditEntryDto(String adminEmail, String action, String detail, Instant createdAt) {

    public static AuditEntryDto from(AdminAuditLog a) {
        return new AuditEntryDto(a.getAdminEmail(), a.getAction(), a.getDetail(), a.getCreatedAt());
    }
}
