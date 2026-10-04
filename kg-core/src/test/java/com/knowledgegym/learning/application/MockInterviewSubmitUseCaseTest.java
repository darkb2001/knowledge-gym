package com.knowledgegym.learning.application;

import com.knowledgegym.content.domain.model.ModuleRef;
import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.Topic;
import com.knowledgegym.content.domain.port.ModuleRepository;
import com.knowledgegym.content.domain.port.TopicRepository;
import com.knowledgegym.learning.application.LearningTestSupport.InMemoryQuestionRepository;
import com.knowledgegym.learning.domain.model.InterviewAnswer;
import com.knowledgegym.learning.domain.model.InterviewSession;
import com.knowledgegym.learning.domain.port.InterviewSessionRepository;
import com.knowledgegym.shared.application.ConflictException;
import com.knowledgegym.shared.application.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mock interview theo mô hình "kết thúc phỏng vấn = submit toàn cục". Trọng tâm:
 * 1. Submit 1 request cho mọi câu, câu bỏ trống **không** tạo row rỗng nhưng vẫn có mặt trong
 *    trang kết quả (user luôn được redirect sang đó, kể cả khi không trả lời gì).
 * 2. Trang kết quả trả đủ câu của phiên + `answerHtml` để tự đối chiếu, không có điểm số.
 * 3. Phiên đã đóng thì submit lần hai là 409; câu không thuộc phiên là 400 và không lưu gì.
 */
class MockInterviewSubmitUseCaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID TOPIC_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-02T09:00:00Z"), ZoneOffset.UTC);

    private InMemoryQuestionRepository questions;
    private InterviewSessionRepositoryFake sessions;
    private MockInterviewUseCase useCase;

    @BeforeEach
    void setUp() {
        questions = new InMemoryQuestionRepository();
        for (int order = 1; order <= 3; order++) {
            questions.seed(LearningTestSupport.question(MODULE_ID, "Câu " + order, order));
        }
        InterviewModuleRepository modules = new InterviewModuleRepository();
        ModuleRef module = new ModuleRef("Chuyên đề 1", "chuyen-de-1", 1);
        module.setId(MODULE_ID);
        module.setTopicId(TOPIC_ID);
        modules.seed(module);

        InMemoryTopicRepository topics = new InMemoryTopicRepository();
        Topic topic = new Topic("Java", "java", 1);
        topic.setId(TOPIC_ID);
        topics.seed(topic);

        sessions = new InterviewSessionRepositoryFake();
        useCase = new MockInterviewUseCase(sessions, questions, modules, topics, CLOCK);
    }

    private MockInterviewUseCase.Started start(int count) {
        return useCase.start(USER_ID, TOPIC_ID, count, "TEXT");
    }

    @Test
    void submitMotRequestChoMoiCauVaTraTrangKetQuaDayDu() {
        var started = start(3);
        var prompts = started.questions();

        var result = useCase.submit(USER_ID, started.session().id(), List.of(
                new MockInterviewUseCase.AnswerInput(prompts.get(0).questionId(), "  câu một  "),
                new MockInterviewUseCase.AnswerInput(prompts.get(1).questionId(), "   "),
                new MockInterviewUseCase.AnswerInput(prompts.get(2).questionId(), "câu ba")));

        assertThat(result.session().status()).isEqualTo("FINISHED");
        assertThat(result.session().finishedAt()).isEqualTo(Instant.now(CLOCK));
        assertThat(result.items()).hasSize(3);
        assertThat(result.answeredCount()).isEqualTo(2);
        // Đáp án mẫu có cho MỌI câu — kể cả câu user bỏ trống.
        assertThat(result.items()).allSatisfy(item -> {
            assertThat(item.title()).isNotBlank();
            assertThat(item.answerHtml()).isNotBlank();
        });
        assertThat(result.items().get(0).userAnswer()).isEqualTo("câu một");
        assertThat(result.items().get(1).userAnswer()).isNull();
        // Bỏ trống thì không ghi row rỗng vào DB.
        assertThat(sessions.answersOf(started.session().id())).hasSize(2);
    }

    @Test
    void submitKhongTraLoiCauNaoVanDongPhienVaCoTrangKetQua() {
        var started = start(2);

        var result = useCase.submit(USER_ID, started.session().id(), List.of());

        assertThat(result.session().status()).isEqualTo("FINISHED");
        assertThat(result.answeredCount()).isZero();
        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).allSatisfy(item -> assertThat(item.userAnswer()).isNull());
    }

    @Test
    void resultDocLaiSauSubmitTraDungCauTraLoiCuaPhien() {
        var started = start(2);
        useCase.submit(USER_ID, started.session().id(), List.of(
                new MockInterviewUseCase.AnswerInput(started.questions().get(1).questionId(), "chỉ trả lời câu 2")));

        var reopened = useCase.result(USER_ID, started.session().id());

        assertThat(reopened.items()).hasSize(2);
        assertThat(reopened.items().get(1).userAnswer()).isEqualTo("chỉ trả lời câu 2");
        assertThat(reopened.items().get(1).answeredAt()).isEqualTo(Instant.now(CLOCK));
        assertThat(reopened.items().get(0).userAnswer()).isNull();
    }

    @Test
    void activeInterviewCannotExposeSampleAnswersThroughResult() {
        var started = start(2);
        assertThatThrownBy(() -> useCase.result(USER_ID, started.session().id()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> useCase.result(OTHER_USER_ID, started.session().id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(sessions.answersOf(started.session().id())).isEmpty();
        useCase.submit(USER_ID, started.session().id(), List.of());
        assertThat(useCase.result(USER_ID, started.session().id()).items()).hasSize(2);
    }

    @Test
    void submitLanHaiTrenPhienDaDongTraConflict() {
        var started = start(1);
        useCase.submit(USER_ID, started.session().id(), List.of());

        assertThatThrownBy(() -> useCase.submit(USER_ID, started.session().id(), List.of()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void submitCauKhongThuocPhienBiTuChoiVaKhongLuuGi() {
        var started = start(2);

        assertThatThrownBy(() -> useCase.submit(USER_ID, started.session().id(), List.of(
                new MockInterviewUseCase.AnswerInput(UUID.randomUUID(), "câu lạ"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(sessions.answersOf(started.session().id())).isEmpty();
    }

    @Test
    void submitQua100CauBiTuChoi() {
        var started = start(1);
        UUID questionId = started.questions().getFirst().questionId();
        List<MockInterviewUseCase.AnswerInput> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(new MockInterviewUseCase.AnswerInput(questionId, "x"));
        }

        assertThatThrownBy(() -> useCase.submit(USER_ID, started.session().id(), tooMany))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void phienCuaNguoiKhacKhongSubmitDuoc() {
        var started = start(1);

        assertThatThrownBy(() -> useCase.submit(OTHER_USER_ID, started.session().id(), List.of()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resumeTraCauTheoThuTuVaMetadata() {
        var started = start(3);
        sessions.upsertAnswer(new InterviewAnswer(started.session().id(),
                started.questions().get(1).questionId(), "đáp án 2", null, Instant.now(CLOCK)));

        var resume = useCase.resume(USER_ID, started.session().id());

        assertThat(resume.session().status()).isEqualTo("ACTIVE");
        assertThat(resume.totalQuestions()).isEqualTo(3);
        assertThat(resume.answeredCount()).isEqualTo(1);
        assertThat(resume.questions()).hasSize(3);
        assertThat(resume.questions()).allSatisfy(q -> assertThat(q.title()).isNotBlank());
        assertThat(resume.questions().get(0).answered()).isFalse();
        assertThat(resume.questions().get(1).answered()).isTrue();
        assertThat(resume.questions().get(2).answered()).isFalse();
    }

    @Test
    void resumePhienCuaNguoiKhacKhongDocDuoc() {
        var started = start(1);

        assertThatThrownBy(() -> useCase.resume(OTHER_USER_ID, started.session().id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelSetTrangThaiTerminalVaIdempotent() {
        var started = start(2);

        var cancelled = useCase.cancel(USER_ID, started.session().id());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.finishedAt()).isEqualTo(Instant.now(CLOCK));

        var again = useCase.cancel(USER_ID, started.session().id());
        assertThat(again.status()).isEqualTo("CANCELLED");

        assertThat(useCase.resume(USER_ID, started.session().id()).session().status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelPhienCuaNguoiKhacBiChan() {
        var started = start(1);

        assertThatThrownBy(() -> useCase.cancel(OTHER_USER_ID, started.session().id()))
                .isInstanceOf(NotFoundException.class);
    }

    /** Fake giữ đúng bất biến của adapter thật: `answersOf` chỉ trả câu đã trả lời, `finish` set status. */
    static final class InterviewSessionRepositoryFake implements InterviewSessionRepository {

        private final Map<UUID, InterviewSession> sessions = new LinkedHashMap<>();
        private final Map<UUID, Map<UUID, InterviewAnswer>> answers = new LinkedHashMap<>();

        @Override
        public InterviewSession save(InterviewSession session) {
            sessions.put(session.id(), session);
            return session;
        }

        @Override
        public Optional<InterviewSession> findByIdAndUserIdForUpdate(UUID id, UUID userId) {
            return findByIdAndUserId(id, userId);
        }

        @Override
        public Optional<InterviewSession> findByIdAndUserId(UUID id, UUID userId) {
            return Optional.ofNullable(sessions.get(id)).filter(session -> session.userId().equals(userId));
        }

        @Override
        public Map<UUID, InterviewAnswer> answersOf(UUID sessionId) {
            return Map.copyOf(answers.getOrDefault(sessionId, Map.of()));
        }

        @Override
        public List<InterviewSession> findByUserId(UUID userId, int page, int size) {
            return sessions.values().stream().filter(session -> session.userId().equals(userId)).toList();
        }

        @Override
        public long countByUserId(UUID userId) {
            return sessions.values().stream().filter(session -> session.userId().equals(userId)).count();
        }

        @Override
        public void upsertAnswer(InterviewAnswer answer) {
            answers.computeIfAbsent(answer.sessionId(), key -> new LinkedHashMap<>())
                    .put(answer.questionId(), answer);
        }

        @Override
        public void finish(UUID sessionId, Instant finishedAt) {
            InterviewSession current = sessions.get(sessionId);
            sessions.put(sessionId, new InterviewSession(current.id(), current.userId(), current.topicId(),
                    current.questionCount(), current.mode(), "FINISHED", current.startedAt(), finishedAt,
                    current.questionIds()));
        }

        @Override
        public void cancel(UUID sessionId, Instant finishedAt) {
            InterviewSession current = sessions.get(sessionId);
            sessions.put(sessionId, new InterviewSession(current.id(), current.userId(), current.topicId(),
                    current.questionCount(), current.mode(), "CANCELLED", current.startedAt(), finishedAt,
                    current.questionIds()));
        }
    }

    static final class InterviewModuleRepository implements ModuleRepository {

        private final List<ModuleRef> store = new ArrayList<>();

        void seed(ModuleRef module) {
            store.add(module);
        }

        @Override
        public Optional<ModuleRef> findBySlug(String slug) {
            return store.stream().filter(module -> module.getSlug().equals(slug)).findFirst();
        }

        @Override
        public Optional<ModuleRef> findById(UUID id) {
            return store.stream().filter(module -> module.getId().equals(id)).findFirst();
        }

        @Override
        public ModuleRef saveOrUpdateBySlug(ModuleRef module) {
            seed(module);
            return module;
        }

        @Override
        public List<ModuleRef> findByTopicIdOrdered(UUID topicId) {
            return store.stream().filter(module -> topicId.equals(module.getTopicId())).toList();
        }

        @Override
        public List<ModuleRef> findAllOrdered() {
            return List.copyOf(store);
        }

        @Override
        public long countQuestions(UUID moduleId) {
            return 0;
        }

        @Override
        public Map<UUID, Long> countQuestionsByModule() {
            return Map.of();
        }
    }

    static final class InMemoryTopicRepository implements TopicRepository {

        private final List<Topic> store = new ArrayList<>();

        void seed(Topic topic) {
            store.add(topic);
        }

        @Override
        public Optional<Topic> findBySlug(String slug) {
            return store.stream().filter(topic -> topic.getSlug().equals(slug)).findFirst();
        }

        @Override
        public Topic saveOrUpdateBySlug(Topic topic) {
            seed(topic);
            return topic;
        }

        @Override
        public List<Topic> findAllOrdered() {
            return List.copyOf(store);
        }
    }
}
