package com.knowledgegym.identity.application;

/**
 * Auth domain exception — presentation map Kind → HTTP status.
 * UNAUTHORIZED: credentials/token invalid (401)
 * BAD_REQUEST: validation (400)
 * CONFLICT: duplicate resource (409)
 */
public class AuthException extends RuntimeException {

    public enum Kind { UNAUTHORIZED, BAD_REQUEST, CONFLICT }

    private final Kind kind;

    public AuthException(String message) {
        this(Kind.UNAUTHORIZED, message);
    }

    public AuthException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}