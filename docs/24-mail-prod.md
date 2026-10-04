# 24 — Mail transactional trên prod (quên mật khẩu / mã đặt lại)

## Điểm dễ mất dữ liệu niềm tin: mail là OPT-IN, mặc định TẮT

`MAIL_SMTP_ENABLED` mặc định `false` (`docker-compose.prod.yml` → `application-prod.yml`
→ `app.email.smtp-enabled`). Khi false, `GmailEmailService` chạy nhánh dev:

```java
if (!smtpEnabled) {
    log.warn("[DEV-FALLBACK] Password reset code for {}: {}", email, code);
    return;
}
```

⇒ **Không thư nào được gửi.** Người dùng bấm "quên mật khẩu" sẽ không nhận gì, còn mã
6 số nằm nguyên trong log container (trái với ghi chú trong chính class: prod không
bao giờ log mã). Đây là kiểu lỗi im lặng: API vẫn trả 200, DB vẫn lưu mã.

## Bật (đã áp trên prod 2026-10-03)

`.env` trên CT102:

```bash
MAIL_USERNAME=<tài khoản gmail gửi thư>
MAIL_PASSWORD=<app password 16 ký tự — KHÔNG phải mật khẩu đăng nhập>
MAIL_SMTP_ENABLED=true
```

rồi recreate app: `docker compose up -d app` (hoặc để CI deploy recreate — env đổi thì
compose tự recreate).

Ghi chú cấu hình:

- SMTP: `smtp.gmail.com:587`, STARTTLS + auth (`application.yml`, hardcode host/port).
  Tài khoản Gmail thường: ~500 thư/ngày, ~100 người nhận/thư → đủ cho giai đoạn đầu.
- `APP_EMAIL_FROM` (compose, mặc định `no-reply@knowledgegym.dev`): Gmail **rewrite**
  header From thành địa chỉ tài khoản nếu giá trị không phải alias đã verify trong
  Gmail. Muốn From đúng domain (`no-reply@…`) → chuyển sang relay có domain riêng
  (Brevo SMTP) rồi set biến này. Đã kiểm bằng `check-auth@verifier.port25.com`:
  SPF pass / DKIM pass / iprev pass, thư tới domain ngoài bình thường.
- **Không** bật `MAIL_HEALTH_ENABLED=true` trên prod: health indicator sẽ probe SMTP,
  Gmail chậm/chặn là `/actuator/health` DOWN → deploy gate của CI đỏ oan.

## Nghiệm thu end-to-end (phải làm sau mỗi lần đổi mail/secret)

```bash
API=https://api.darkb-tech.io.vn/api/v1
# 1. đăng ký 1 user bằng hộp thư ĐỌC ĐƯỢC (dùng alias + của hộp thư mình kiểm soát)
curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"<hop-thu>","password":"MatKhau12345","displayName":"mail test"}'
# 2. quên mật khẩu
curl -s -X POST $API/auth/forgot-password -H 'Content-Type: application/json' \
  -d '{"email":"<hop-thu>"}'
# 3. mở hộp thư: thư "Knowledge Gym — Mã đặt lại mật khẩu", mã 6 số, hiệu lực 10 phút
# 4. dùng chính mã trong thư để đặt lại
curl -s -X POST $API/auth/reset-password -H 'Content-Type: application/json' \
  -d '{"email":"<hop-thu>","code":"<ma-trong-mail>","newPassword":"MatKhau67890"}'
# 5. đăng nhập lại bằng mật khẩu mới để chốt
```

**Đạt khi:** thư vào inbox (không spam) **và** bước 4 trả thành công **và** bước 5 login được.
Log app: thành công có `Password reset code sent to <email>`; lỗi có
`Failed to send password reset email` + HTTP 500 (fail loud, không log mã).

Kiểm SMTP độc lập với app (không cần deploy):

```bash
python3 - <<'PY'
import smtplib, ssl
from email.message import EmailMessage
m=EmailMessage(); m["From"]=m["To"]="<gmail tài khoản>"; m["Subject"]="smtp test"; m.set_content("x")
s=smtplib.SMTP("smtp.gmail.com",587,timeout=30); s.starttls(context=ssl.create_default_context())
s.login("<gmail tài khoản>","<app password>"); s.send_message(m); s.quit(); print("ok")
PY
```

## Còn thiếu (chưa chặn, nên xử lý)

- **Rate limit** cho `/auth/forgot-password`: hiện không có → spam được mã / làm cạn quota Gmail
  (P2, xem `handoff-dev-fixes-4.md` D15).
- **Giám sát deliverability**: không có theo dõi bounce/spam; chỉ có Alertmanager cho ops.
- Mailu self-host vẫn deferred (ADR-004 / `docs/19-m11b-ops.md`).
- Rotate ngay app password Gmail nếu nó từng bị lộ trong log/chat, và nhớ: rotate xong
  **phải chạy lại nghiệm thu ở trên** (SMTP sai mật khẩu thì app ném 500 khi gửi).

## Ảnh đại diện người gửi trong hộp thư (không sửa được bằng header)

Hộp thư hiện ảnh/logo cạnh người gửi theo 3 nguồn, **không** phải do header `From`:

1. **Tài khoản Google đang gửi** — Gmail hiển thị ảnh hồ sơ của chính tài khoản đó. Tài khoản
   chưa đặt ảnh → Gmail vẽ vòng tròn chữ cái đầu (đây là tình trạng hiện tại). Cách sửa rẻ nhất:
   đặt ảnh hồ sơ (logo vuông ≥250×250) cho tài khoản gửi.
2. **BIMI + VMC** — logo domain trong Gmail/Apple Mail/Yahoo, nhưng cần domain riêng có DMARC
   `p=reject` **và** chứng chỉ VMC (trả phí, cần nhãn hiệu đăng ký).
3. **Khác** — mọi trường hợp còn lại: vòng tròn chữ cái đầu, không có API/header nào đổi được.

Thư trong repo đã là HTML (bản text dự phòng) + tên hiển thị "Knowledge Gym"
(`APP_EMAIL_FROM_NAME`), màu accent trùng web, không nhúng ảnh ngoài nên không bị chặn
"hiển thị hình ảnh". Muốn biểu tượng thật (logo) → chọn BIMI+VMC hoặc Google Workspace.
