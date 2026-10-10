package com.knowledgegym.identity.application;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.model.User;
import com.knowledgegym.identity.domain.port.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh token rotation — 2 lớp (Redis O(1) + PostgreSQL fallback).
 *
 * Ordering (sửa multi-tab session-kill từ Redis-first):
 * 1. Absolute family lifetime (nếu policy bật) — quá hạn cứng thì revoke + buộc login lại
 * 2. Reuse checks (family revoked / blacklist) — revoke BỀN VỮNG rồi mới 401
 * 3. Lookup Redis → PG fallback
 * 4. PG CAS revoke old + insert new (atomic winner)
 * 5. Redis store new + blacklist old (sau khi PG thắng)
 *
 * CAS thua (concurrent) → 401 KHÔNG revokeFamily (winner giữ session).
 *
 * <p><strong>Revoke trong nhánh reuse phải đi qua {@link RefreshFamilyRevoker}</strong>, không dùng
 * {@code refreshTokenRepository.revokeFamily} trực tiếp: nhánh reuse ném {@code AuthException} trong
 * {@code @Transactional}, nên nếu revoke nằm chung transaction đó thì lệnh UPDATE bị rollback theo và
 * family vẫn sống (bug đã đóng). Revoker commit ở transaction riêng và set cả Redis.
 */
public class RefreshTokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenUseCase.class);

    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCachePort cache;
    private final UserRepository userRepository;
    private final RefreshFamilyRevoker familyRevoker;

    public RefreshTokenUseCase(TokenService tokenService,
                               RefreshTokenRepository refreshTokenRepository,
                               RefreshTokenCachePort cache,
                               UserRepository userRepository,
                               RefreshFamilyRevoker familyRevoker) {
        this.tokenService = tokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.cache = cache;
        this.userRepository = userRepository;
        this.familyRevoker = familyRevoker;
    }

    public record Result(String accessToken, String newRefreshToken, UUID userId, String role) {}

    /**
     * Rotation không áp absolute family lifetime.
     *
     * @deprecated dùng {@link #execute(String, String, String, Duration)} để policy 30 ngày được áp.
     */
    @Deprecated
    @Transactional
    public Result execute(String rawRefreshToken, String ipAddress, String userAgent) {
        return rotate(rawRefreshToken, ipAddress, userAgent, null);
    }

    /**
     * Rotation có absolute family lifetime: dù rotate bao nhiêu lần, sau
     * {@code absoluteFamilyTtl} kể từ lần login đầu tiên của family, người dùng phải xác thực lại.
     * {@code null} hoặc duration không dương (≤ 0) = tắt (hành vi cũ, rolling 7 ngày thuần).
     */
    @Transactional
    public Result execute(String rawRefreshToken, String ipAddress, String userAgent,
                          Duration absoluteFamilyTtl) {
        Duration effectiveTtl = absoluteFamilyTtl == null || absoluteFamilyTtl.isZero()
                || absoluteFamilyTtl.isNegative() ? null : absoluteFamilyTtl;
        return rotate(rawRefreshToken, ipAddress, userAgent, effectiveTtl);
    }

    private Result rotate(String rawRefreshToken, String ipAddress, String userAgent,
                          Duration absoluteFamilyTtl) {
        String oldHash = HashUtils.sha256Hex(rawRefreshToken);

        TokenService.RefreshTokenClaims claims;
        try {
            claims = tokenService.verifyRefreshToken(rawRefreshToken);
        } catch (Exception e) {
            throw new AuthException("Invalid refresh token");
        }

        UUID userId = claims.userId();
        UUID familyId = claims.familyId();

        // (1) Hạn tuyệt đối theo family: rolling TTL 7 ngày không được phép gia hạn vô thời hạn.
        // Token cũ (phát trước khi có claim) không mang mốc family → bỏ qua check này, không được
        // tự bịa mốc (sẽ đá oan phiên đang hoạt động).
        if (absoluteFamilyTtl != null && claims.familyIssuedAt() != null
                && Instant.now().isAfter(claims.familyIssuedAt().plus(absoluteFamilyTtl))) {
            throw revokeAndThrow(familyId,
                    "Refresh session exceeded its maximum lifetime — please sign in again");
        }

        // (2) Reuse detection. Cả hai nhánh phải revoke bền vững trước khi ném 401.
        if (cache.isFamilyRevoked(familyId)) {
            throw revokeAndThrow(familyId, "Refresh token family revoked — reuse detected");
        }

        // Blacklisted = đã rotated thành công trước đó → TRUE reuse (stolen/old tab)
        if (cache.isBlacklisted(oldHash)) {
            throw revokeAndThrow(familyId, "Refresh token reuse detected — family revoked");
        }

        Optional<RefreshTokenCachePort.CacheEntry> cacheEntry = cache.find(oldHash);

        if (cacheEntry.isEmpty()) {
            Optional<RefreshToken> auditOpt = refreshTokenRepository.findByTokenHash(oldHash);
            if (auditOpt.isEmpty()) {
                throw new AuthException("Refresh token not found in cache or database");
            }
            RefreshToken audit = auditOpt.get();
            if (audit.isRevoked()) {
                // PG đã revoke (rotation trước) nhưng Redis miss → reuse
                throw revokeAndThrow(familyId, "Refresh token already revoked");
            }
            cache.store(oldHash, audit.getUserId(), audit.getFamilyId(), RefreshToken.TTL);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User no longer exists"));

        if (user.isBlocked()) {
            throw new AuthException("Account is blocked");
        }
        if (!user.isEmailVerified()) {
            throw new AuthException("Email verification required");
        }

        String newAccessToken = tokenService.generateAccessToken(user.getId(), user.getRole().name());
        // Giữ mốc neo của family khi rotate: nếu tạo token mới với familyIssuedAt = now thì
        // absolute lifetime biến thành no-op (mỗi lần rotate lại gia hạn thêm 30 ngày).
        String newRawRefresh = claims.familyIssuedAt() == null
                ? tokenService.generateRefreshToken(user.getId(), familyId)
                : tokenService.generateRefreshToken(user.getId(), familyId, claims.familyIssuedAt());
        String newHash = HashUtils.sha256Hex(newRawRefresh);

        // PG-first: CAS revoke old — chỉ 1 concurrent thread thắng
        RefreshToken newAudit = new RefreshToken(userId, newHash, familyId);
        newAudit.setIpAddress(ipAddress);
        newAudit.setUserAgent(userAgent);
        RefreshToken savedNew = refreshTokenRepository.save(newAudit);

        Optional<RefreshToken> oldOpt = refreshTokenRepository.findByTokenHash(oldHash);
        if (oldOpt.isPresent()) {
            boolean revoked = refreshTokenRepository.revokeIfActive(oldOpt.get().getId(), savedNew.getId());
            if (!revoked) {
                // Concurrent loser — KHÔNG revokeFamily (winner vẫn valid)
                throw new AuthException("Refresh token already rotated — retry with latest cookie");
            }
        }

        // Redis sau PG thắng — blacklist + store new
        cache.blacklist(oldHash, RefreshToken.TTL);
        cache.store(newHash, userId, familyId, RefreshToken.TTL);

        return new Result(newAccessToken, newRawRefresh, userId, user.getRole().name());
    }

    /**
     * Revoke family ở transaction riêng rồi trả về exception để bên gọi ném.
     *
     * <p>Nuốt lỗi hạ tầng có chủ đích: nếu revoke thất bại (DB/Redis blip) thì request vẫn phải 401 —
     * đúng hành vi cũ — nhưng lỗi được log ở ERROR để còn phát hiện family chưa bị cắt.
     */
    private AuthException revokeAndThrow(UUID familyId, String message) {
        try {
            familyRevoker.revokeDurably(familyId);
        } catch (RuntimeException revokeFailure) {
            log.error("Durable revoke failed for refresh family {} — session not fully contained",
                    familyId, revokeFailure);
        }
        return new AuthException(message);
    }
}
