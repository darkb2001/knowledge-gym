package com.knowledgegym.identity.application;

/** Auth domain exception — map sang HTTP status ở presentation layer. */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}