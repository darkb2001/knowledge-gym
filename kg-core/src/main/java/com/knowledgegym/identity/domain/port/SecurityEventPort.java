package com.knowledgegym.identity.domain.port;

import java.util.UUID;

/**
 * Audit hook cho sự kiện bảo mật tầng identity (đổi / đặt mật khẩu, xung đột liên kết OAuth).
 * kg-core thuần Java — không biết bảng `audit_logs`; infrastructure implement (SpringAuditLogger).
 */
public interface SecurityEventPort {

    void passwordChanged(UUID userId, String ipAddress);

    void passwordSet(UUID userId, String ipAddress);

    void oauthEmailConflict(String email, String ipAddress);
}
