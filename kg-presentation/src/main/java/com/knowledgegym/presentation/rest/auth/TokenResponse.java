package com.knowledgegym.presentation.rest.auth;

/** Login/Register response — access token body + refresh cookie (httpOnly). */
public record TokenResponse(
        String accessToken,
        long expiresIn,
        UserResponse user) {

    public record UserResponse(String id, String email, String displayName, String role) {}
}