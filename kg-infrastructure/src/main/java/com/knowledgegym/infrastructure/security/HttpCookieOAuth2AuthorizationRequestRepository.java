package com.knowledgegym.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lưu authorization request của OAuth2 (state + PKCE {@code code_verifier}) trong một cookie
 * đã mã hoá AES-GCM thay vì HTTP session.
 *
 * <p>Vì sao: app này chạy {@code SessionCreationPolicy.STATELESS}, nhưng repository mặc định
 * ({@code HttpSessionOAuth2AuthorizationRequestRepository}) vẫn ghi state vào {@code JSESSIONID}
 * — một <em>session cookie</em> không có {@code Max-Age}. Trình duyệt (đặc biệt iOS/Safari khi
 * chuyển app sang trang đăng nhập Google, hoặc khi người dùng xoá cookie) rất dễ đánh rơi
 * session cookie, và lúc đó callback trả về {@code authorization_request_not_found} →
 * người dùng chỉ thấy "đăng nhập thất bại" ngẫu nhiên.
 *
 * <p>Cookie ở đây là cookie <em>bền</em> (Max-Age ngắn), {@code SameSite=Lax} nên vẫn được gửi
 * trong lượt điều hướng GET từ Google, và nội dung được mã hoá + xác thực (GCM tag) nên không
 * thể bị đọc hay sửa từ phía client.
 */
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE_NAME = "kg_oauth_req";

    /** State chỉ cần sống trong một vòng Google; ngắn để giảm cửa sổ replay. */
    static final int MAX_AGE_SECONDS = 300;

    private static final String KEY_CONTEXT = "|kg-oauth2-authreq-v1";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String FIELD_SEPARATOR = "&";
    private static final String ENTRY_SEPARATOR = "|";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public HttpCookieOAuth2AuthorizationRequestRepository(
            @Value("${app.security.jwt.access-secret}") String accessSecret) {
        this.key = new SecretKeySpec(deriveKey(accessSecret), "AES");
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        if (authorizationRequest == null) {
            expireCookie(request, response);
            return;
        }
        String value = encrypt(encode(authorizationRequest));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(value, MAX_AGE_SECONDS, request).toString());
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String raw = readCookie(request);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return decode(decrypt(raw));
        } catch (Exception ex) {
            // Cookie hỏng/giả mạo/khoá đã đổi → coi như không có, để user đăng nhập lại sạch sẽ
            // thay vì ném lỗi 500 ở tầng filter.
            return null;
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                HttpServletResponse response) {
        OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
        // State dùng một lần: xoá cookie ngay cả khi load thất bại.
        expireCookie(request, response);
        return loaded;
    }

    // ------------------------------------------------------------------ cookie

    private ResponseCookie cookie(String value, long maxAgeSeconds, HttpServletRequest request) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .path(cookiePath(request))
                .maxAge(maxAgeSeconds)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .build();
    }

    private void expireCookie(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0, request).toString());
    }

    private String cookiePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return (contextPath == null || contextPath.isEmpty()) ? "/" : contextPath;
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ mã hoá

    private static byte[] deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secret.getBytes(StandardCharsets.UTF_8));
            digest.update(KEY_CONTEXT.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (Exception ex) {
            throw new IllegalStateException("Không dẫn xuất được khoá mã hoá state OAuth2", ex);
        }
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Không mã hoá được authorization request OAuth2", ex);
        }
    }

    private String decrypt(String value) throws Exception {
        byte[] raw = Base64.getUrlDecoder().decode(value);
        if (raw.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Payload quá ngắn");
        }
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(raw, IV_LENGTH, raw.length - IV_LENGTH), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ serialize

    static String encode(OAuth2AuthorizationRequest req) {
        StringBuilder sb = new StringBuilder();
        field(sb, "v", "1");
        field(sb, "au", req.getAuthorizationUri());
        field(sb, "ci", req.getClientId());
        field(sb, "ru", req.getRedirectUri());
        field(sb, "st", req.getState());
        field(sb, "sc", String.join(" ", req.getScopes() == null ? Set.<String>of() : req.getScopes()));
        field(sb, "ap", encodePairs(req.getAdditionalParameters()));
        field(sb, "at", encodePairs(req.getAttributes()));
        return sb.toString();
    }

    static OAuth2AuthorizationRequest decode(String payload) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : payload.split(FIELD_SEPARATOR)) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            values.put(pair.substring(0, idx), decodeValue(pair.substring(idx + 1)));
        }
        String scopes = values.getOrDefault("sc", "");
        Set<String> scopeSet = new LinkedHashSet<>();
        for (String scope : scopes.trim().split(" ")) {
            if (!scope.isBlank()) {
                scopeSet.add(scope);
            }
        }
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(values.get("au"))
                .clientId(values.get("ci"))
                .redirectUri(values.get("ru"))
                .state(values.get("st"))
                .scopes(scopeSet)
                .additionalParameters(decodePairs(values.get("ap")))
                .attributes(decodePairs(values.get("at")))
                .build();
    }

    private static void field(StringBuilder sb, String name, String value) {
        if (value == null) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(FIELD_SEPARATOR);
        }
        sb.append(name).append('=').append(encodeValue(value));
    }

    private static String encodePairs(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> {
            if (v == null) {
                return;
            }
            if (!sb.isEmpty()) {
                sb.append(ENTRY_SEPARATOR);
            }
            sb.append(encodeValue(k)).append('=').append(encodeValue(String.valueOf(v)));
        });
        return sb.toString();
    }

    private static Map<String, Object> decodePairs(String encoded) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return map;
        }
        for (String entry : encoded.split("\\" + ENTRY_SEPARATOR)) {
            int idx = entry.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            map.put(decodeValue(entry.substring(0, idx)), decodeValue(entry.substring(idx + 1)));
        }
        return map;
    }

    private static String encodeValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decodeValue(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
