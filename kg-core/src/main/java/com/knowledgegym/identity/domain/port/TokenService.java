package com.knowledgegym.identity.domain.port;

import java.util.UUID;

public interface TokenService {
    String generateAccessToken(UUID userId, String role);
    String generateRefreshToken(UUID userId, UUID familyId);
    TokenPayload verifyAccessToken(String token);

    record TokenPayload(UUID userId, String role, String jti) {}
}
