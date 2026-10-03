package com.knowledgegym.identity.domain.port;

/** Atomic, expiring, purpose-specific registration challenges. Never stores plaintext codes. */
public interface EmailVerificationPort {
    boolean issue(String email, String codeHash);
    boolean consume(String email, String codeHash);
    void invalidate(String email, String codeHash);
}
