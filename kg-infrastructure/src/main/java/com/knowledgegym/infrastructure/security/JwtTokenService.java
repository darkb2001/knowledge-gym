package com.knowledgegym.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.knowledgegym.identity.domain.port.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenService implements TokenService {

    public static final String CLAIM_FAMILY_ID = "fid";
    /**
     * Mốc login đầu tiên của family, mili-giây epoch. Rotation giữ NGUYÊN claim này để absolute
     * lifetime (ví dụ 30 ngày) không bị gia hạn vô thời hạn bởi rolling TTL 7 ngày.
     */
    public static final String CLAIM_FAMILY_ISSUED_AT_MILLIS = "fiatMs";
    /**
     * iat chuẩn (NumericDate) chỉ có độ phân giải GIÂY, không đủ để so với mốc cắt phiên
     * ({@code users.tokens_invalid_before} lưu mili-giây): access token phát trong cùng giây
     * với lúc logout của thiết bị khác sẽ bị coi là "cũ" và trả 401 oan. Claim này ghi thêm
     * mốc phát hành chính xác tới mili-giây; xem JdbcAccessTokenGuard.
     */
    public static final String CLAIM_ISSUED_AT_MILLIS = "iatMs";
    public static final String CLAIM_ROLE = "role";

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final Duration accessTtl = Duration.ofMinutes(15);
    private final Duration refreshTtl = Duration.ofDays(7);

    public JwtTokenService(
            @Value("${app.security.jwt.access-secret}") String accessSecret,
            @Value("${app.security.jwt.refresh-secret}") String refreshSecret) {
        this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateAccessToken(UUID userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_ISSUED_AT_MILLIS, now.toEpochMilli())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(accessKey)
                .compact();
    }

    @Override
    public String generateRefreshToken(UUID userId, UUID familyId) {
        return generateRefreshToken(userId, familyId, Instant.now());
    }

    @Override
    public String generateRefreshToken(UUID userId, UUID familyId, Instant familyIssuedAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_FAMILY_ID, familyId.toString())
                .claim(CLAIM_FAMILY_ISSUED_AT_MILLIS, familyIssuedAt.toEpochMilli())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .signWith(refreshKey)
                .compact();
    }

    @Override
    public TokenPayload verifyAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(accessKey)
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new TokenPayload(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_ROLE, String.class),
                claims.getId());
    }

    @Override
    public RefreshTokenClaims verifyRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(refreshKey)
                .clockSkewSeconds(30)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        // Token phát trước khi có claim family lifetime → null. KHÔNG thay bằng iat: iat là mốc
        // rotate hiện tại nên sẽ biến absolute expiry thành no-op.
        Number familyIssuedAtMillis = claims.get(CLAIM_FAMILY_ISSUED_AT_MILLIS, Number.class);
        Instant familyIssuedAt = familyIssuedAtMillis == null
                ? null
                : Instant.ofEpochMilli(familyIssuedAtMillis.longValue());
        return new RefreshTokenClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(CLAIM_FAMILY_ID, String.class)),
                familyIssuedAt);
    }
}
