package com.knowledgegym.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HttpOnly refresh-token cookie — dùng chung AuthController + OAuth2SuccessHandler.
 * Policy: httpOnly + Secure(prod) + Path=/ + SameSite + TTL 7d.
 * (ArchUnit chỉ cấm presentation → infrastructure.persistence; security helper OK.)
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "refreshToken";
    public static final int MAX_AGE_SECONDS = 7 * 24 * 3600;

    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookie(@Value("${app.security.cookie.secure:false}") boolean secure,
                              @Value("${app.security.cookie.same-site:Strict}") String sameSite) {
        // SameSite=None mà không Secure thì browser drop cookie im lặng (không có log phía server)
        // — fail fast lúc startup thay vì để auth hỏng trên prod.
        if ("None".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalStateException(
                    "app.security.cookie.same-site=None yêu cầu secure=true (browser drop cookie cross-site không Secure)");
        }
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public void write(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie(NAME, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(MAX_AGE_SECONDS);
        cookie.setAttribute("SameSite", sameSite);
        response.addCookie(cookie);
    }

    public void clear(HttpServletResponse response) {
        Cookie cookie = new Cookie(NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", sameSite);
        response.addCookie(cookie);
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}