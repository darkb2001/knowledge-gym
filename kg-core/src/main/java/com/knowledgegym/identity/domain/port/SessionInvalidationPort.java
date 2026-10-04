package com.knowledgegym.identity.domain.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Vô hiệu hoá access token đã phát hành trước mốc thời gian cho trước.
 *
 * Access token là JWT stateless nên không thể "xoá"; thay vào đó mọi request đều tra
 * {@code users.tokens_invalid_before} (xem JdbcAccessTokenGuard) và token có {@code iat}
 * không mới hơn mốc đó bị coi như hết hiệu lực. Nhờ vậy logout/password change cắt phiên
 * ngay lập tức thay vì phải chờ access token hết hạn (15 phút).
 *
 * KHÔNG đụng tới refresh token: refresh family do LogoutUseCase/RefreshTokenUseCase quản lý
 * riêng, nên các thiết bị khác vẫn refresh được và không bị đá ra ngoài.
 */
public interface SessionInvalidationPort {

    void invalidateIssuedBefore(UUID userId, Instant cutoff);
}
