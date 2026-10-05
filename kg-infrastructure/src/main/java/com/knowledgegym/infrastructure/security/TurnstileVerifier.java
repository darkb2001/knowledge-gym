package com.knowledgegym.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Xác thực Cloudflare Turnstile ở phía server (`siteverify`).
 *
 * <p>Vì sao cần lớp này: rate limit theo IP chỉ chặn được flood từ một IP; bot spam đăng ký/quên mật khẩu
 * thường xoay IP. Turnstile buộc mỗi yêu cầu phải kèm token do trình duyệt thật giải.
 *
 * <p>Khi {@code app.turnstile.enabled=false} (mặc định) mọi yêu cầu đi thẳng — nhờ vậy có thể deploy
 * code trước, bật secret sau mà không làm gãy luồng đăng ký đang chạy.
 */
@Component
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);
    private static final String DEFAULT_VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final boolean enabled;
    private final boolean failOpen;
    private final String secret;
    private final String verifyUrl;
    private final RestClient http;

    public TurnstileVerifier(
            @Value("${app.turnstile.enabled:false}") boolean enabled,
            @Value("${app.turnstile.secret:}") String secret,
            @Value("${app.turnstile.verify-url:" + DEFAULT_VERIFY_URL + "}") String verifyUrl,
            @Value("${app.turnstile.timeout-ms:3000}") long timeoutMs,
            @Value("${app.turnstile.fail-open:true}") boolean failOpen) {
        this.enabled = enabled;
        this.failOpen = failOpen;
        this.secret = secret == null ? "" : secret.trim();
        this.verifyUrl = verifyUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(java.time.Duration.ofMillis(timeoutMs));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isEnabled() {
        return enabled && !secret.isEmpty();
    }

    /** @return true nếu yêu cầu được phép đi tiếp. */
    public boolean verify(String token, String remoteIp) {
        if (!isEnabled()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            log.warn("Turnstile: yêu cầu thiếu token");
            return false;
        }
        try {
            Map<String, Object> body = postVerify(token.trim(), remoteIp);
            Object success = body == null ? null : body.get("success");
            if (Boolean.TRUE.equals(success)) {
                return true;
            }
            log.warn("Turnstile: token bị từ chối, error-codes={}", body == null ? "null" : body.get("error-codes"));
            return false;
        } catch (RuntimeException ex) {
            // Cloudflare lỗi/timeout: mặc định KHÔNG chặn người dùng thật (fail-open) và ghi log để biết.
            log.warn("Turnstile: không gọi được siteverify ({}), fail-open={}", ex.getMessage(), failOpen);
            return failOpen;
        }
    }

    /** Tách riêng để test override, không gọi mạng thật. */
    protected Map<String, Object> postVerify(String token, String remoteIp) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }
        return http.post()
                .uri(verifyUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    /** Các mã lỗi Cloudflare trả về — dùng cho log/telemetry. */
    public static List<String> errorCodes(Map<String, Object> body) {
        Object codes = body == null ? null : body.get("error-codes");
        return codes instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }
}
