package com.knowledgegym.learning.application;

import com.knowledgegym.learning.domain.port.AdminLearningPort;
import com.knowledgegym.shared.domain.model.PageResult;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

public class AdminLearningUseCase {
    private final AdminLearningPort learning;
    public AdminLearningUseCase(AdminLearningPort learning) { this.learning = learning; }
    public PageResult<AdminLearningPort.Entry> list(UUID userId, AdminLearningPort.Kind kind, int page, int size) {
        if (userId == null || kind == null || page < 1 || size < 1 || size > 100)
            throw new IllegalArgumentException("userId/kind bắt buộc, page >= 1, size 1..100");
        return learning.list(userId,kind,page,size);
    }
    @Transactional public void resetCard(UUID actor, UUID userId, UUID cardId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("reason bắt buộc, tối đa 500 ký tự");
        learning.resetCard(actor,userId,cardId,reason.trim());
    }
}
