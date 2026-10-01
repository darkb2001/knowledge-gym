package com.knowledgegym.content.domain.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chuyển `answer_html` thành text thuần để sinh option MCQ — domain service thuần Java, không dùng
 * Jsoup (Jsoup nằm ở infrastructure; domain không được kéo dependency của tầng ngoài vào).
 *
 * <p>Không cố gắng parse HTML đúng chuẩn: `answer_html` đã qua {@code AnswerHtmlSanitizer} từ lúc
 * import nên chỉ còn thẻ trong whitelist, không có `script`/`style`. Regex đủ dùng và không kéo theo
 * engine parse nào.
 */
public final class PlainText {

    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);");

    private PlainText() {
    }

    /** Bỏ thẻ HTML + giải mã entity thường gặp; giữ xuống dòng để {@code firstSentence} cắt được. */
    public static String of(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = TAG.matcher(html).replaceAll(" ");
        text = decodeEntities(text);
        // &nbsp; sau khi giải mã thành khoảng trắng thường (U+00A0) vẫn không khớp \s ở một số chỗ.
        return text.replace('\u00A0', ' ').trim();
    }

    private static String decodeEntities(String text) {
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement = switch (matcher.group(1)) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                default -> numericEntity(matcher.group(1), matcher.group());
            };
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String numericEntity(String body, String raw) {
        try {
            int codePoint = body.startsWith("#x") || body.startsWith("#X")
                    ? Integer.parseInt(body.substring(2), 16)
                    : body.startsWith("#") ? Integer.parseInt(body.substring(1)) : -1;
            return codePoint > 0 ? new String(Character.toChars(codePoint)) : raw;
        } catch (RuntimeException e) {
            return raw;
        }
    }
}
