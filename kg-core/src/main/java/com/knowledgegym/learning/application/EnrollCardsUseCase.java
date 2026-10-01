package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionQuery;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.QuestionRepository;
import com.knowledgegym.learning.domain.model.SrsDeck;
import com.knowledgegym.learning.domain.port.SRSCardRepository;
import com.knowledgegym.learning.domain.port.SrsDeckRepository;
import com.knowledgegym.shared.application.NotFoundException;
import com.knowledgegym.shared.domain.model.PageResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enroll câu hỏi vào SRS — một endpoint, hai mode (thống nhất với `08-rest-api.md`):
 * <ul>
 *   <li><b>Mode A</b> ({@code moduleId}) — "Start flashcards" từ trang module: lấy toàn bộ câu của
 *       module, auto tìm/tạo deck theo module.</li>
 *   <li><b>Mode B</b> ({@code questionIds}) — danh sách cụ thể (custom deck, bookmark ở m08).</li>
 * </ul>
 *
 * <p><b>Idempotent</b> nhờ UK `(user_id, question_id)` + `ON CONFLICT DO NOTHING`: gọi lại không
 * tạo thẻ trùng **và không reset lịch ôn** của thẻ đã có. Vì vậy `enrolled` là số thẻ *mới*, không
 * phải số id gửi lên — client retry không được thấy con số khác nhau giữa hai lần gọi.
 *
 * <p>Deck theo module được reuse qua {@code findByUserIdAndModuleId} (V004 không có UK
 * `(user_id, module_id)`, nên application là hàng rào duy nhất chống deck trùng).
 *
 * <p><b>`deckId` trả về là thông tin, không phải nguồn chân lý cho membership.</b> `ON CONFLICT DO
 * NOTHING` không ghi đè thẻ đã có, nên thẻ enroll trước đó ở mode B (không deck) sẽ **giữ**
 * `deck_id = NULL` dù lần này enroll theo module trả về `deckId`. Hệ quả: `deckId` trong response
 * mô tả deck *được dùng cho các thẻ mới*, không phải "mọi thẻ trong danh sách giờ thuộc deck này".
 * Mọi view lọc theo deck phải đọc `srs_cards.deck_id` chứ không suy từ response enroll — hoặc
 * backfill `deck_id` khi cần ngữ nghĩa "thêm vào deck" thật (chưa làm ở m5 vì chưa có màn deck).
 */
public class EnrollCardsUseCase {

    /** Tên deck auto = tên module; cắt theo `VARCHAR(200)` của `srs_decks.name`. */
    private static final int MAX_DECK_NAME_LENGTH = 200;

    /**
     * Trần số câu mỗi lần enroll mode B. Không có giới hạn thì một request có thể mang hàng chục
     * nghìn id: mỗi id là một phần tử của mảng literal trong câu INSERT và request vẫn phải được
     * xử lý trọn vẹn trước khi trả lỗi — khuếch đại tài nguyên theo ý client. 500 đủ rộng cho mọi
     * luồng thật (bookmark theo chủ đề, deck tự tạo), và vẫn là con số mà 1 câu INSERT xử lý gọn.
     */
    static final int MAX_QUESTION_IDS = 500;

    private final QuestionRepository questionRepository;
    private final ModuleRepository moduleRepository;
    private final SRSCardRepository cardRepository;
    private final SrsDeckRepository deckRepository;
    private final Clock clock;

    public EnrollCardsUseCase(QuestionRepository questionRepository,
                              ModuleRepository moduleRepository,
                              SRSCardRepository cardRepository,
                              SrsDeckRepository deckRepository,
                              Clock clock) {
        this.questionRepository = questionRepository;
        this.moduleRepository = moduleRepository;
        this.cardRepository = cardRepository;
        this.deckRepository = deckRepository;
        this.clock = clock;
    }

    public record EnrollCommand(UUID moduleId, List<UUID> questionIds, UUID deckId) {}

    public record EnrollResult(int enrolled, List<UUID> cardIds, UUID deckId) {}

    @Transactional
    public EnrollResult execute(UUID userId, EnrollCommand command) {
        List<UUID> targetQuestionIds;
        UUID deckId;

        if (command.moduleId() != null && command.questionIds() != null && !command.questionIds().isEmpty()) {
            throw new IllegalArgumentException("Chỉ gửi một trong hai: moduleId (enroll cả module) hoặc questionIds");
        }

        if (command.moduleId() != null) {
            if (command.deckId() != null) {
                throw new IllegalArgumentException("deckId chỉ dùng khi enroll theo questionIds");
            }
            ModuleRef module = moduleRepository.findById(command.moduleId())
                    .orElseThrow(() -> new NotFoundException("Module không tồn tại: " + command.moduleId()));
            targetQuestionIds = questionsOfModule(module.getId()).stream().map(Question::getId).toList();
            if (targetQuestionIds.isEmpty()) {
                // Không tạo deck rỗng: enroll module chưa có câu hỏi là no-op thật sự.
                return new EnrollResult(0, List.of(), null);
            }
            deckId = resolveModuleDeck(userId, module);
        } else if (command.questionIds() != null && !command.questionIds().isEmpty()) {
            targetQuestionIds = dedupe(command.questionIds());
            if (targetQuestionIds.size() > MAX_QUESTION_IDS) {
                throw new IllegalArgumentException(
                        "questionIds vượt quá " + MAX_QUESTION_IDS + " phần tử: " + targetQuestionIds.size());
            }
            requireQuestionsExist(targetQuestionIds);
            deckId = resolveRequestedDeck(userId, command.deckId());
        } else {
            throw new IllegalArgumentException("Cần moduleId hoặc questionIds (không được để trống cả hai)");
        }

        List<UUID> created = cardRepository.insertIgnoringDuplicates(
                userId, deckId, targetQuestionIds, LocalDate.now(clock));
        return new EnrollResult(created.size(), created, deckId);
    }

    /**
     * Mode B: id trùng trong request chỉ tạo 1 thẻ — `LinkedHashSet` giữ thứ tự để `cardIds` trả về
     * theo đúng thứ tự client gửi.
     *
     * <p>Phần tử `null` bị chặn tường minh: `List.copyOf` ném `NullPointerException` (không có
     * handler → 500) trong khi đây là input sai định dạng của client → phải là 400. Dùng `stream`
     * thay vì `contains(null)` vì `ImmutableCollections.contains(null)` cũng ném NPE.
     */
    private static List<UUID> dedupe(List<UUID> questionIds) {
        if (questionIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("questionIds không được chứa phần tử null");
        }
        return List.copyOf(new LinkedHashSet<>(questionIds));
    }

    /**
     * Câu hỏi không tồn tại mà vẫn insert sẽ vi phạm FK `srs_cards.question_id` → 500 khó hiểu.
     * Chặn ở đây để trả 404 kèm id cụ thể.
     *
     * <p>Dùng {@code findByIds} (1 query) thay vì lặp {@code findById}: mode B nhận list dài tuỳ ý
     * từ client nên vòng lặp là N+1 và là vector khuếch đại request. Id thiếu được dò trong bộ nhớ
     * từ kết quả trả về nên đường lỗi cũng không phát sinh query thêm.
     */
    private void requireQuestionsExist(List<UUID> questionIds) {
        Set<UUID> found = questionRepository.findByIds(questionIds).stream()
                .map(Question::getId)
                .collect(Collectors.toSet());
        for (UUID id : questionIds) {
            if (!found.contains(id)) {
                throw new NotFoundException("Câu hỏi không tồn tại: " + id);
            }
        }
    }

    /** Deck đã có thì reuse; chưa có thì tạo mới (name = tên module). */
    private UUID resolveModuleDeck(UUID userId, ModuleRef module) {
        return deckRepository.findByUserIdAndModuleId(userId, module.getId())
                .map(SrsDeck::getId)
                .orElseGet(() -> deckRepository.save(
                        SrsDeck.forModule(userId, module.getId(), truncate(module.getName()))).getId());
    }

    /**
     * Mode B: `deckId` là tuỳ chọn — không gửi thì thẻ nằm ngoài deck (`deck_id` NULL, hợp lệ theo
     * V004). Gửi deck của người khác phải 404, không phải âm thầm gán vào deck đó.
     */
    private UUID resolveRequestedDeck(UUID userId, UUID deckId) {
        if (deckId == null) {
            return null;
        }
        return deckRepository.findByIdAndUserId(deckId, userId)
                .map(SrsDeck::getId)
                .orElseThrow(() -> new NotFoundException("Deck không tồn tại: " + deckId));
    }

    /**
     * Toàn bộ câu của module. Đi qua `search` (đã có trong port) và lặp theo `MAX_SIZE` thay vì
     * thêm method mới vào port — module > 100 câu vẫn enroll đủ, không bị cắt im lặng.
     */
    private List<Question> questionsOfModule(UUID moduleId) {
        List<Question> all = new ArrayList<>();
        int page = 1;
        while (true) {
            PageResult<Question> result = questionRepository.search(
                    new QuestionQuery(moduleId, null, null, null, page, QuestionQuery.MAX_SIZE));
            all.addAll(result.items());
            if (result.items().size() < QuestionQuery.MAX_SIZE) {
                return all;
            }
            page++;
        }
    }

    private static String truncate(String name) {
        String safe = name == null || name.isBlank() ? "Deck" : name.trim();
        return safe.length() <= MAX_DECK_NAME_LENGTH ? safe : safe.substring(0, MAX_DECK_NAME_LENGTH);
    }
}
