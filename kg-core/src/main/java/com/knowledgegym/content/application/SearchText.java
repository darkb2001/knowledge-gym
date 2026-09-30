package com.knowledgegym.content.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sinh token cho cột `questions.searchable_text`.
 *
 * Mục đích: cầu nối giữa câu hỏi và **từ người học thực sự gõ**. Câu trong docs/ gần như
 * không chứa từ tiếng Việt thông dụng ("sao lưu", "bất đồng bộ"), và nhiều khái niệm chỉ
 * xuất hiện dưới dạng viết tắt (GC, JVM, N+1). Tokenize thuần từ title/answer sẽ miss.
 *
 * Bốn cơ chế bù:
 * 1. Bỏ dấu — người gõ không dấu vẫn match (`khong dong bo` → `không đồng bộ`).
 * 2. n-gram (2–3 từ) từ title/tags — cụm như `bất đồng bộ` trở thành token `bat-dong-bo`.
 * 3. Tách slug gạch nối — tag `jvm-basics` cũng sinh `jvm`, `basics`.
 * 4. Bảng song ngữ Việt–Anh — `sao-luu` ⟷ `backup`/`replication`.
 *
 * PostgreSQL dùng config `simple` (chỉ lowercase + tokenize) vì nội dung trộn 2 ngôn ngữ;
 * stemming tiếng Anh sẽ băm nát từ tiếng Việt.
 */
public final class SearchText {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._]*");
    private static final int MAX_TOKENS = 140;
    private static final int MIN_LEN = 2;
    private static final int MAX_NGRAM = 3;

    /**
     * Stopword — chỉ gồm hư từ **không bao giờ** là nội dung.
     *
     * Cố ý KHÔNG đưa các từ đa nghĩa vào đây dù chúng cũng là hư từ: `sao` (sao lưu = backup),
     * `cau` (cấu trúc), `phan` (phân trang/phân quyền), `khoa` (khoá), `do` (độ). Loại chúng
     * khỏi index sẽ làm hỏng đúng những cụm từ khoá đắt giá nhất.
     */
    private static final Set<String> STOPWORDS = Stream.of(
            // tiếng Việt — hư từ thuần
            "la", "va", "cua", "cho", "voi", "khi", "nay", "trong", "tren", "duoi",
            "sau", "truoc", "bang", "khong", "duoc", "se", "vi", "nhu", "hoac", "hay",
            "mot", "cac", "thi", "nao", "gi",
            // tiếng Anh
            "the", "and", "or", "of", "to", "in", "is", "are", "was", "were", "for",
            "on", "at", "by", "with", "from", "as", "an", "it", "its", "be", "been",
            "this", "that", "these", "those", "what", "which", "when", "how", "why",
            "you", "your", "we", "our", "they", "their", "not", "but", "if", "than",
            "then", "so", "such", "can", "could", "should", "would", "will", "shall",
            "does", "did", "have", "has", "had", "use", "used", "using",
            "between", "into", "over", "under", "about", "more", "most", "some", "any",
            "all", "each", "other", "another", "same", "different", "eg", "vd"
    ).collect(Collectors.toUnmodifiableSet());

    /**
     * Nhóm đồng nghĩa Việt–Anh — chạm 1 vế thì thêm tất cả các vế còn lại.
     * Khoá phải là token **thực sự xuất hiện**: từ đơn, hoặc n-gram gạch nối không dấu
     * (vd `bat-dong-bo` sinh từ title "bất đồng bộ").
     */
    private static final List<String[]> SYNONYMS = List.of(
            new String[]{"sao-luu", "backup", "replication", "replica"},
            new String[]{"khoa", "lock", "locking", "deadlock"},
            new String[]{"luong", "thread", "threading"},
            new String[]{"tien-trinh", "tien", "process"},
            new String[]{"bo-nho", "memory", "heap", "stack"},
            new String[]{"rac", "garbage", "gc", "thu-hoi-rac"},
            new String[]{"giao-dich", "transaction", "acid"},
            new String[]{"chi-muc", "index", "indexing"},
            new String[]{"truy-van", "query", "sql"},
            new String[]{"phan-trang", "pagination"},
            new String[]{"xac-thuc", "authentication", "authn"},
            new String[]{"phan-quyen", "authorization", "rbac", "authz"},
            new String[]{"phien", "session", "cookie"},
            new String[]{"hang-doi", "queue", "message"},
            new String[]{"bo-dem", "cache", "caching"},
            new String[]{"can-bang-tai", "balancing", "balancer"},
            new String[]{"ngat-mach", "circuit", "breaker"},
            new String[]{"bat-dong-bo", "async", "asynchronous"},
            new String[]{"dong-bo", "sync", "synchronous"},
            new String[]{"tuan-tu", "serial", "serialization"},
            new String[]{"bang", "table", "schema"},
            new String[]{"khoa-chinh", "primary", "fk"},
            new String[]{"khoa-ngoai", "foreign", "fk"},
            new String[]{"doi-tuong", "object", "instance"},
            new String[]{"giao-dien", "interface", "api"},
            new String[]{"da-hinh", "polymorphism"},
            new String[]{"ke-thua", "inheritance", "extends"},
            new String[]{"dong-goi", "encapsulation"},
            new String[]{"ngoai-le", "exception", "error"},
            new String[]{"kiem-thu", "test", "testing"},
            new String[]{"trien-khai", "deploy", "deployment"},
            new String[]{"theo-doi", "monitor", "monitoring", "observability"},
            new String[]{"nhat-quan", "consistency", "consistent"},
            new String[]{"san-sang", "availability", "available"},
            new String[]{"chia-de-tri", "divide", "conquer"},
            new String[]{"do-phuc-tap", "complexity", "big-o"},
            new String[]{"ma-hoa", "encrypt", "encryption", "hash"},
            new String[]{"bao-mat", "security", "secure"},
            new String[]{"tran", "overflow", "underflow"},
            new String[]{"xung-dot", "conflict", "collision"},
            new String[]{"mau-thiet-ke", "pattern"},
            new String[]{"bo-nho-tam", "cache", "buffer"},
            new String[]{"thoi-gian", "timeout"},
            new String[]{"ket-noi", "connection"},
            new String[]{"sap-xep", "sort", "ordering"},
            new String[]{"tim-kiem", "search", "lookup"},
            new String[]{"du-lieu", "data", "dataset"},
            new String[]{"cau-truc", "structure"},
            new String[]{"thuat-toan", "algorithm"},
            new String[]{"hieu-nang", "performance"}
    );

    private SearchText() {
    }

    /**
     * @param title       tiêu đề câu hỏi — nguồn n-gram chính (cụm từ khoá đắt giá nhất).
     * @param answerText  nội dung trả lời đã bỏ HTML.
     * @param tags        tag slug từ section (vd `jvm-basics`).
     * @return token cách nhau bằng space, lowercase, đã bỏ trùng và cắt theo {@link #MAX_TOKENS}.
     */
    public static String build(String title, String answerText, String tags) {
        Set<String> tokens = new LinkedHashSet<>();
        // Thứ tự có ý nghĩa: title + n-gram + synonym là phần "đắt" và ngắn, phải vào trước.
        // Answer rất dài (thật: median ~146 token/câu) và sẽ nuốt hết MAX_TOKENS nếu chạy trước,
        // khiến n-gram/synonym bị cắt ở guard — đúng những token mà search tiếng Việt cần nhất.
        collectWords(title, tokens);
        collectNgrams(title, tokens);
        collectNgrams(tags, tokens);
        collectWords(tags, tokens);

        // Answer gom riêng: nó vừa là **trigger** cho nhóm synonym (vd `garbage` trong câu trả lời
        // → kéo theo `thu-hoi-rac`), vừa là thứ không được chiếm chỗ của n-gram/synonym. Gộp thẳng
        // vào `tokens` rồi mới applySynonyms thì answer dài ăn hết MAX_TOKENS và nhóm synonym
        // không bao giờ fire; còn applySynonyms trước khi biết answer thì bỏ sót đúng nhóm đó.
        Set<String> answerTokens = new LinkedHashSet<>();
        collectWords(answerText, answerTokens);

        Set<String> triggers = new LinkedHashSet<>(tokens);
        triggers.addAll(answerTokens);
        applySynonyms(tokens, triggers);

        for (String token : answerTokens) {
            if (tokens.size() >= MAX_TOKENS) {
                break;
            }
            tokens.add(token);
        }
        return String.join(" ", tokens);
    }

    /**
     * Chuẩn hoá truy vấn người dùng về **đúng không gian token của index**: bỏ dấu + loại hư từ,
     * ghép lại bằng space. Trả `""` khi không còn token nội dung nào.
     *
     * Bắt buộc phải làm ở phía Java, không thể giao cho PostgreSQL: config `simple` chỉ lowercase
     * và tokenize, **không biết hư từ tiếng Việt**. `plainto_tsquery('simple', 'khong dong bo')`
     * sinh `'khong' & 'dong' & 'bo'`, mà index không có token `khong` (index giữ `không`, còn
     * `"khong"` nằm trong {@link #STOPWORDS} nên đã bị loại). Người gõ gần như luôn kèm hư từ
     * ("không đồng bộ", "là gì", "của JVM"), nên không chuẩn hoá thì truy vấn tự nhiên gần như
     * luôn ra 0 hit dù dữ liệu có.
     *
     * Không thể là SQL/DB-side normalization tương đương vì Java mới có bảng stopword + bỏ dấu
     * tiếng Việt; đây là lý do index và query phải dùng **cùng** hàm này.
     */
    public static String normalizeQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = WORD.matcher(raw.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < MIN_LEN || isNumeric(token)) {
                continue;
            }
            String plain = stripDiacritics(token);
            if (STOPWORDS.contains(token) || STOPWORDS.contains(plain)) {
                continue;
            }
            tokens.add(plain.length() >= MIN_LEN ? plain : token);
        }
        return String.join(" ", tokens);
    }

    private static void collectWords(String raw, Set<String> out) {
        if (raw == null || raw.isBlank() || out.size() >= MAX_TOKENS) {
            return;
        }
        Matcher matcher = WORD.matcher(raw.toLowerCase(Locale.ROOT));
        while (matcher.find() && out.size() < MAX_TOKENS) {
            addToken(matcher.group(), out);
        }
    }

    private static void addToken(String token, Set<String> out) {
        if (token.length() < MIN_LEN || isNumeric(token) || STOPWORDS.contains(token)) {
            return;
        }
        String plain = stripDiacritics(token);
        if (STOPWORDS.contains(plain)) {
            return;
        }
        out.add(token);
        if (!plain.equals(token) && plain.length() >= MIN_LEN) {
            out.add(plain);
        }
        // Tag/slug gạch nối cũng sinh từng phần: `jvm-basics` → `jvm`, `basics`.
        if (token.indexOf('-') > 0) {
            for (String piece : token.split("-")) {
                if (piece.length() >= MIN_LEN && !isNumeric(piece)) {
                    out.add(piece);
                }
                String plainPiece = stripDiacritics(piece);
                if (!plainPiece.equals(piece) && plainPiece.length() >= MIN_LEN) {
                    out.add(plainPiece);
                }
            }
        }
    }

    /**
     * Sinh n-gram 2–3 từ **không dấu** nối bằng `-` từ title/tag.
     *
     * Đây là cơ chế để cụm tiếng Việt trở thành 1 token tra được: "sao lưu" → `sao-luu`,
     * "bất đồng bộ" → `bat-dong-bo`.
     *
     * Stopword bị loại **trước khi** ghép cụm, nên nội dung hai bên hư từ vẫn liền nhau:
     * "difference between List and Set" → `difference-list-set`. Nếu giữ hư từ rồi mới bỏ cụm,
     * mọi cụm đều chứa hư từ và không n-gram nào được sinh ra.
     *
     * Không sinh n-gram từ answer vì câu trả lời dài, n-gram sẽ bùng nổ số lượng.
     */
    private static void collectNgrams(String raw, Set<String> out) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(raw.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String piece = stripDiacritics(matcher.group());
            if (piece.length() < MIN_LEN || isNumeric(piece) || STOPWORDS.contains(piece)) {
                continue;
            }
            words.add(piece);
        }
        for (int size = 2; size <= MAX_NGRAM; size++) {
            for (int start = 0; start + size <= words.size(); start++) {
                if (out.size() >= MAX_TOKENS) {
                    return;
                }
                out.add(String.join("-", words.subList(start, start + size)));
            }
        }
    }

    /**
     * @param target  set được thêm token vào (đã chứa title/n-gram/tag).
     * @param trigger set dùng để phát hiện nhóm synonym — thường là target ∪ token của answer,
     *                để nhóm chỉ xuất hiện trong câu trả lời vẫn kích hoạt được.
     */
    private static void applySynonyms(Set<String> target, Set<String> trigger) {
        for (String[] group : SYNONYMS) {
            boolean hit = false;
            for (String candidate : group) {
                if (trigger.contains(candidate)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                continue;
            }
            for (String candidate : group) {
                if (target.size() >= MAX_TOKENS) {
                    return;
                }
                target.add(candidate);
            }
        }
    }

    private static boolean isNumeric(String token) {
        return token.chars().allMatch(Character::isDigit);
    }

    /** Bỏ dấu tiếng Việt — dùng NFD rồi loại combining marks. */
    public static String stripDiacritics(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        return sb.toString().replace('đ', 'd').replace('Đ', 'D');
    }

    /** Bỏ hết HTML tag — dùng cho text của answer trước khi tokenize. */
    public static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", " ").replaceAll("&[#\\w]+;", " ");
    }

    /** Tách `searchable_text` trở lại thành list token (giữ thứ tự, bỏ trùng). */
    public static List<String> tokenList(String searchableText) {
        if (searchableText == null || searchableText.isBlank()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(List.of(searchableText.trim().split("\\s+"))));
    }
}
