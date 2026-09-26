package com.knowledgegym.identity.domain.model;

/**
 * Cách thức user đăng nhập — enforce compile-time CHECK constraint
 * của DDL: auth_provider IN ('LOCAL', 'GOOGLE', 'GITHUB').
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    GITHUB;

    public static AuthProvider fromDb(String value) {
        return value == null ? LOCAL : valueOf(value);
    }
}
