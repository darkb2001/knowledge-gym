package com.knowledgegym.infrastructure.persistence.adapter;

import com.knowledgegym.identity.domain.model.RefreshToken;
import com.knowledgegym.identity.domain.port.RefreshFamilyRevoker;
import com.knowledgegym.identity.domain.port.RefreshTokenCachePort;
import com.knowledgegym.infrastructure.persistence.repository.SpringDataRefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Thu hồi family bền vững cho nhánh reuse-detection của {@code RefreshTokenUseCase}.
 *
 * <p>{@code REQUIRES_NEW} là điểm mấu chốt: use case ném {@code AuthException} (RuntimeException)
 * ngay sau khi gọi, và mặc định Spring rollback cả transaction → nếu chạy trong cùng transaction
 * thì lệnh UPDATE revoke bị huỷ và family vẫn sống. Transaction riêng ở đây commit độc lập.
 *
 * <p>Cố ý KHÔNG ném khi revoked = 0 (family đã bị revoke trước đó) vì revoke là idempotent: nhánh
 * reuse có thể chạy lặp lại và chỉ cần đảm bảo trạng thái cuối là "đã cắt".
 */
@Component
public class DurableRefreshFamilyRevoker implements RefreshFamilyRevoker {

    private static final Logger log = LoggerFactory.getLogger(DurableRefreshFamilyRevoker.class);

    private final SpringDataRefreshTokenRepository repository;
    private final RefreshTokenCachePort cache;

    public DurableRefreshFamilyRevoker(SpringDataRefreshTokenRepository repository,
                                       RefreshTokenCachePort cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeDurably(UUID familyId) {
        // PG trước: đây là nguồn chân lý và là thứ sống sót qua restart Redis.
        repository.revokeFamily(familyId, Instant.now());
        // Redis best-effort có chủ đích. Nếu để lỗi Redis propagate khỏi method này, Spring sẽ
        // rollback luôn transaction REQUIRES_NEW → mất cả lệnh UPDATE vừa chạy, tức mất
        // containment chỉ vì một lần Redis blip. Vì vậy nuốt lỗi ở đây.
        // Nếu marker Redis không set được: token MỚI NHẤT của family vẫn có key rt:{hash} nên
        // một request refresh bằng nó sẽ đi tiếp tới bước CAS; nhưng row đó đã revoked_at ở PG
        // nên revokeIfActive trả false → 401. Tức containment vẫn giữ nhờ PG, chỉ khác mã lỗi
        // (already-rotated thay vì reuse-detected). Log ERROR để biết mà soi.
        try {
            cache.revokeFamily(familyId, RefreshToken.TTL);
        } catch (RuntimeException redisFailure) {
            log.error("Family {} revoked in PostgreSQL but the Redis marker failed; "
                    + "PG CAS still rejects the family on the next refresh",
                    familyId, redisFailure);
        }
    }
}
