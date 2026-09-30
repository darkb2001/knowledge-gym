package com.knowledgegym.content.application;

/**
 * Lỗi khi đọc/parse nội dung docs/. Presentation map → 400/500 (không leak path nội bộ ra public).
 */
public class ContentImportException extends RuntimeException {

    public ContentImportException(String message) {
        super(message);
    }

    public ContentImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
