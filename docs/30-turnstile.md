# 30 — Cloudflare Turnstile (chống bot ở form gửi mail)

## Vì sao

Rate limit theo IP (`RateLimitFilter`, nginx zone `auth`/`api`) chỉ chặn được flood từ **một** IP.
Bot spam đăng ký / quên mật khẩu thường xoay IP và gọi đúng nhịp dưới ngưỡng. Turnstile buộc mỗi
yêu cầu phải kèm token do **trình duyệt thật** giải, nên đòn "gửi 10.000 email xác minh" mất hiệu lực.

Turnstile **không** đặt ở `/auth/login`: người dùng thật bị chặn oan sẽ mất đường vào tài khoản, trong khi
login đã được bảo vệ bởi rate limit app (5/phút/IP) + zone `auth` ở nginx.

## Thành phần

| Lớp | File | Việc |
| --- | --- | --- |
| FE widget | `components/TurnstileWidget.tsx`, `lib/turnstile.ts` | render explicit, hook `useTurnstile`, reset sau mỗi lần gửi (token dùng 1 lần) |
| FE gửi token | `lib/auth.ts` | header `X-Turnstile-Token` cho register / email-verification / verify-email / forgot / reset |
| BE xác thực | `kg-infrastructure/.../security/TurnstileVerifier.java` | POST `siteverify`, timeout 3s |
| BE chặn | `kg-infrastructure/.../security/TurnstileFilter.java` | 403 `turnstile_failed` khi thiếu/sai token |

Endpoint bị bảo vệ (chỉ `POST`):
`/auth/register`, `/auth/email-verification/request`, `/auth/verify-email`,
`/auth/forgot-password`, `/auth/reset-password`, `/auth/password-code/request`.

## Bật / tắt

Mặc định **tắt** (`app.turnstile.enabled=false`) để có thể deploy code trước rồi mới gắn secret.

```bash
# CT102, /opt/kg/.env  (0600, KHÔNG commit)
APP_TURNSTILE_ENABLED=true
APP_TURNSTILE_SECRET=<secret key từ Cloudflare dashboard>
# tuỳ chọn
APP_TURNSTILE_FAIL_OPEN=true   # Cloudflare timeout/lỗi ⇒ cho qua + log WARN
APP_TURNSTILE_TIMEOUT_MS=3000
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps app
```

Site key là **public**, FE lấy từ `NEXT_PUBLIC_TURNSTILE_SITE_KEY` (mặc định đã có trong `lib/turnstile.ts`).

## Nghiệm thu

```bash
# 1) Thiếu token ⇒ 403 (khi đã bật)
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://api.darkb-tech.io.vn/api/v1/auth/register \
  -H 'Content-Type: application/json' -d '{"email":"x@y.z","password":"12345678","confirmPassword":"12345678","displayName":"x"}'

# 2) Có token thật (lấy từ trình duyệt) ⇒ đi tiếp vào validate (400) chứ không phải 403
# 3) Trên FE: nút "Gửi mã xác minh" bị disable cho tới khi widget giải xong
```

## Bẫy đã gặp

- **Adblocker / mạng chặn `challenges.cloudflare.com`** ⇒ widget không render, nút gửi chết im lặng.
  FE hiện thông báo "Không tải được phần xác minh chống bot…" thay vì để người dùng đoán.
- **Token chỉ dùng được một lần** ⇒ phải `reset()` widget sau mỗi lần gửi (kể cả lần gửi thất bại),
  nếu không lần gửi kế tiếp nhận 403 dù người dùng thấy widget "đã tick".
- **`getRequestURI()` có context-path `/api/v1`** ⇒ filter phải so khớp bằng `getServletPath()`
  (giống `RateLimitFilter`), nếu không rule im lặng không chạy ở production.
- **Fail-open là mặc định có chủ ý**: Cloudflare sự cố hiếm khi xảy ra, nhưng chặn hết đăng ký thì
  thiệt hại lớn hơn spam; log `WARN` + `TURNSTILE_FAIL_OPEN=false` nếu muốn cứng.
