package com.knowledgegym.content.domain.service;

import com.knowledgegym.content.domain.model.Question;
import com.knowledgegym.content.domain.model.QuestionOption;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sinh đáp án MCQ cho 1 câu hỏi từ chính kho nội dung — domain service thuần Java (không Spring, để
 * `DomainLayerArchTest` xanh).
 *
 * <p><b>Vì sao không dùng `question_options` có sẵn?</b> Bảng đó rỗng: docs/ chỉ có câu hỏi tự luận,
 * không có phương án nhiễu. m4 defer "sinh distractor → m6", và đây là chỗ thực hiện.
 *
 * <p><b>Thuật toán:</b>
 * <ol>
 *   <li>Đáp án đúng = câu đầu tiên có nghĩa của `answer_html` sau khi bỏ HTML.</li>
 *   <li>Distractor = câu đầu tiên có nghĩa của **câu trả lời của câu anh em** cùng module (fallback
 *       cùng topic). Chọn câu trả lời (không phải tiêu đề) để mọi option cùng "hình dạng" — 4 tiêu
 *       đề ngắn giữa 1 câu trả lời dài là gợi ý quá lộ cho người đoán.</li>
 *   <li>Deterministic theo seed = `question.id`: cùng dữ liệu vào → cùng option ra, nên test được và
 *       backfill chạy lại không đổi nội dung.</li>
 *   <li>Không đủ distractor (module + topic < 2 câu dùng được) → trả list **rỗng**: câu đó bị loại
 *       khỏi pool MCQ thay vì bịa option rác làm hỏng chất lượng đề.</li>
 * </ol>
 *
 * <p>Không bao giờ sinh 2 option cùng nội dung: trùng nhau thì câu hỏi có 2 đáp án đúng và không có
 * cách chấm nào đúng.
 */
public final class DistractorGenerator {

    /** 1 đáp án đúng + 3 nhiễu — cùng lúc thoả "≥2 option" và không quá dài cho UI. */
    public static final int DEFAULT_OPTION_COUNT = 4;

    /** Dưới 2 option thì câu hỏi không thể là MCQ (không có gì để chọn sai). */
    public static final int MIN_OPTION_COUNT = 2;

    /** Trần độ dài mỗi option — câu trả lời dài nguyên đoạn sẽ tràn UI và lộ đáp án đúng. */
    private static final int MAX_OPTION_LENGTH = 160;

    /**
     * Nhãn `span.ans-label` trong docs (What/Why/How/When...) bị `PlainText` giữ lại thành chữ, nên
     * câu đầu tiên của đáp án thường bắt đầu bằng "What ..." — dính vào option thì 100% phương án
     * cùng một khuôn và trông như dữ liệu rác. Luôn bỏ nhãn trước khi dùng làm text option.
     */
    private static final java.util.regex.Pattern LABEL_PREFIX = java.util.regex.Pattern.compile(
            "^(?i)\\s*(what|why|how|when not|when|which|where|who|"
                    + "nói trong 60 giây|đáp án|answer)\\b[\\s:.\\-—–]*");

    /** Từ dừng — không dùng để đo độ liên quan giữa phương án nhiễu và câu hỏi. */
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "vs", "la", "là", "cua", "của", "cho", "voi",
            "với", "khi", "nao", "nào", "gi", "gì", "cac", "các", "nhung", "những", "mot", "một",
            "duoc", "được", "trong", "khong", "không", "hay", "hoac", "hoặc", "tren", "trên", "tu",
            "từ", "bằng", "để", "làm", "sao", "explain", "giai", "giải", "thich", "thích");

    private DistractorGenerator() {
    }

    /**
     * @param target   câu hỏi cần sinh option (đáp án đúng lấy từ đây)
     * @param siblings câu anh em cùng module (đã loại `target`), ưu tiên trước
     * @param fallback câu cùng topic nhưng khác module — chỉ dùng khi `siblings` không đủ
     * @return option đã sắp `displayOrder` (đáp án đúng **không** cố định ở vị trí 0), hoặc rỗng nếu
     *         không đủ dữ liệu để tạo câu hỏi trắc nghiệm hợp lệ
     */
    public static List<QuestionOption> generate(Question target, List<Question> siblings,
                                                List<Question> fallback) {
        return generate(target, siblings, fallback, DEFAULT_OPTION_COUNT);
    }

    public static List<QuestionOption> generate(Question target, List<Question> siblings,
                                                List<Question> fallback, int optionCount) {
        Objects.requireNonNull(target, "target");
        int wanted = Math.max(MIN_OPTION_COUNT, optionCount);
        if (target.getId() == null) {
            throw new IllegalArgumentException("Câu hỏi phải có id trước khi sinh option (cần làm seed)");
        }

        String correct = firstSentence(target.getAnswerHtml());
        if (correct.isBlank()) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        seen.add(normalizeKey(correct));

        List<String> distractors = new ArrayList<>();
        for (Question candidate : orderedCandidates(target, siblings, fallback)) {
            if (distractors.size() >= wanted - 1) {
                break;
            }
            String text = firstSentence(candidate.getAnswerHtml());
            if (text.isBlank() || !seen.add(normalizeKey(text))) {
                continue;
            }
            distractors.add(text);
        }
        if (distractors.size() < MIN_OPTION_COUNT - 1) {
            return List.of();
        }

        // Đáp án đúng được chèn ở vị trí biến thiên theo seed: luôn để option đúng ở index 0 thì
        // người học chỉ cần bấm ô đầu tiên là đúng hết. `Math.floorMod` để seed âm không vỡ.
        int correctIndex = Math.floorMod(seedOf(target.getId()), distractors.size() + 1);
        List<QuestionOption> options = new ArrayList<>(distractors.size() + 1);
        int nextDistractor = 0;
        for (int index = 0; index <= distractors.size(); index++) {
            if (index == correctIndex) {
                options.add(new QuestionOption(correct, true, index));
            } else {
                options.add(new QuestionOption(distractors.get(nextDistractor++), false, index));
            }
        }
        return List.copyOf(options);
    }

    /**
     * Gộp siblings + fallback rồi xáo **deterministic** theo seed của câu đích.
     *
     * <p>Không dùng `Collections.shuffle(Random)` mặc định vì `java.util.Random` ổn định theo seed
     * nhưng thứ tự phụ thuộc kích thước list; tự sắp theo hash của `(seed, id)` cho kết quả rõ ràng,
     * dễ suy luận và không phụ thuộc JDK.
     */
    private static List<Question> orderedCandidates(Question target, List<Question> siblings,
                                                    List<Question> fallback) {
        List<Question> pool = new ArrayList<>();
        Set<UUID> seenIds = new LinkedHashSet<>();
        seenIds.add(target.getId());
        for (List<Question> source : List.of(
                siblings == null ? List.<Question>of() : siblings,
                fallback == null ? List.<Question>of() : fallback)) {
            for (Question question : source) {
                if (question == null || question.getId() == null || !seenIds.add(question.getId())) {
                    continue;
                }
                pool.add(question);
            }
        }
        long seed = seedOf(target.getId());
        // Thứ tự ưu tiên: cùng module trước, rồi **câu hỏi liên quan nhất tới chủ đề đang hỏi**.
        // Không có bước liên quan thì phương án nhiễu thường trả lời một câu hỏi khác hẳn, người học
        // chỉ cần đọc đề là loại được — đúng lỗi đã gặp ở dữ liệu quiz.
        pool.sort(java.util.Comparator
                .comparingInt((Question q) -> Objects.equals(target.getModuleId(), q.getModuleId()) ? 0 : 1)
                .thenComparing(java.util.Comparator.comparingInt(
                        (Question q) -> -relevance(target, q)))
                .thenComparingLong(q -> mixedHash(seed, q.getId()))
                .thenComparing(q -> q.getId().toString()));
        return pool;
    }

    /**
     * Số từ khoá chung giữa **câu hỏi đích** và câu trả lời của ứng viên — càng cao thì phương án
     * nhiễu càng bàn về đúng chủ đề đang hỏi (thay vì lạc sang câu khác trong module).
     */
    private static int relevance(Question target, Question candidate) {
        Set<String> topic = keywords(target.getTitle());
        if (topic.isEmpty()) {
            return 0;
        }
        Set<String> answer = keywords(firstSentence(candidate.getAnswerHtml()));
        answer.retainAll(topic);
        return answer.size();
    }

    private static Set<String> keywords(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> words = new LinkedHashSet<>();
        for (String token : PlainText.of(text).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() > 2 && !STOP_WORDS.contains(token)) {
                words.add(token);
            }
        }
        return words;
    }

    /** 64 bit đầu của UUID — đủ trải cho seed, không cần hash mạnh. */
    private static long seedOf(UUID id) {
        return id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    }

    private static long mixedHash(long seed, UUID id) {
        long value = seed ^ (id.getMostSignificantBits() * 0x9E3779B97F4A7C15L);
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return value;
    }

    /**
     * Câu đầu tiên của text. Cắt theo `.`/`!`/`?`/xuống dòng, fallback cả đoạn nếu không có dấu —
     * nhiều câu trả lời trong docs/ là 1 câu dài không kết thúc bằng dấu chấm.
     */
    static String firstSentence(String html) {
        String text = LABEL_PREFIX.matcher(PlainText.of(html)).replaceFirst("").trim();
        if (text.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[.!?](\\s|$)").matcher(text);
        String sentence = matcher.find() ? text.substring(0, matcher.end()).trim() : text;
        sentence = sentence.replaceAll("\\s+", " ").trim();
        return sentence.length() <= MAX_OPTION_LENGTH
                ? sentence
                : trimToWordBoundary(sentence, MAX_OPTION_LENGTH);
    }

    /** Cắt mà không chặt giữa từ — option cụt giữa từ trông như lỗi render. */
    private static String trimToWordBoundary(String text, int max) {
        String cut = text.substring(0, max);
        int lastSpace = cut.lastIndexOf(' ');
        return (lastSpace > max / 2 ? cut.substring(0, lastSpace) : cut).trim() + "…";
    }

    /**
     * So sánh nội dung bỏ qua khác biệt vô nghĩa (hoa/thường, khoảng trắng, dấu câu cuối). Không
     * dùng `equals` thô vì hai câu trả lời cùng nội dung nhưng khác dấu câu vẫn là 2 đáp án trùng.
     */
    private static String normalizeKey(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", " ")
                .trim();
    }
}
