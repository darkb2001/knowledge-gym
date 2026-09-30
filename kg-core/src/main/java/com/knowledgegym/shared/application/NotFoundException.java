package com.knowledgegym.shared.application;

/**
 * Tài nguyên không tồn tại → presentation map 404 (khác {@code ContentImportException} = 400).
 * Tách khỏi AuthException vì đây là lỗi tra cứu, không phải lỗi xác thực.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
