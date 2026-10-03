package com.knowledgegym.infrastructure.security;

import com.knowledgegym.identity.domain.port.SecurityEventPort;
import com.knowledgegym.infrastructure.persistence.entity.AuditLogJpaEntity;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataAuditLogRepository;
import com.knowledgegym.search.domain.port.SearchAuditPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Small infrastructure adapter for security events that have no core use case. */
@Component
public class SpringAuditLogger implements SearchAuditPort, SecurityEventPort {

    private final SpringDataAuditLogRepository repository;

    public SpringAuditLogger(SpringDataAuditLogRepository repository) {
        this.repository = repository;
    }

    public void oauthSuccess(UUID userId, String ipAddress) {
        save(userId, "LOGIN_SUCCESS", ipAddress, null);
    }

    public void oauthFailure(String ipAddress, String reason) {
        save(null, "LOGIN_FAILED", ipAddress, "{\"reason\":\"" + escape(reason) + "\"}");
    }

    @Override
    public void passwordChanged(UUID userId, String ipAddress) {
        save(userId, "PASSWORD_CHANGED", ipAddress, null);
    }

    @Override
    public void passwordSet(UUID userId, String ipAddress) {
        save(userId, "PASSWORD_SET", ipAddress, null);
    }

    @Override
    public void oauthEmailConflict(String email, String ipAddress) {
        save(null, "OAUTH_EMAIL_CONFLICT", ipAddress,
                "{\"email\":\"" + escape(email) + "\"}");
    }

    @Override
    public void record(UUID actorId, String action, String details) {
        String payload = details == null || details.isBlank()
                ? null
                : details.trim().startsWith("{") ? details :
                "{\"output\":\"" + escape(details) + "\"}";
        save(actorId, action, null, payload);
    }

    private void save(UUID userId, String action, String ipAddress, String details) {
        AuditLogJpaEntity event = new AuditLogJpaEntity();
        event.setId(UUID.randomUUID());
        event.setUserId(userId);
        event.setAction(action);
        event.setIpAddress(ipAddress);
        event.setDetails(details);
        event.setCreatedAt(Instant.now());
        repository.save(event);
    }

    private static String escape(String value) {
        if (value == null) return "unknown";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
