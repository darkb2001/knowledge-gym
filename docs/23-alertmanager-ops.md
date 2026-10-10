# Alertmanager — ops stack riêng (project `kgops`)

> Trạng thái: **đang chạy trên CT102** (thêm 03/10). Tài liệu này để dev/ops dựng lại được từ repo, không cần hỏi lại người đã dựng.

## 1. Vì sao tách project

Alertmanager **không** nằm trong `docker-compose.prod.yml`. Mỗi lần CI deploy chạy
`docker compose up -d --remove-orphans`, service nào không có trong file compose đang
dùng sẽ bị xoá — nên ops stack chạy ở project riêng `kgops` với file riêng
`ops/docker-compose.ops.yml`, chỉ dùng chung network `kg_kg-internal` (external).

Kết quả: deploy app không đụng tới Alertmanager; Alertmanager restart không đụng app.

## 2. File trong repo và file chỉ-nằm-trên-host

| File | Trạng thái |
|---|---|
| `ops/docker-compose.ops.yml` | ✅ commit — không chứa secret |
| `ops/alertmanager/alertmanager.example.yml` | ✅ commit — bản mẫu, password để placeholder |
| `ops/alertmanager/alertmanager.yml` | ❌ **gitignore** — bản thật, **không chứa secret** (trỏ tới `*_file:`) |
| `ops/alertmanager/smtp_password` | ❌ **gitignore** — Gmail App Password, `0640 root:nogroup`, mount `:ro` vào `/etc/alertmanager/smtp_password` |
| `ops/alertmanager/telegram_bot_token` | ❌ **gitignore** — token bot Telegram, `0640 root:nogroup`, mount `:ro` vào `/etc/alertmanager/telegram_bot_token` |
| `infra/prometheus.yml` (block `alerting`) | ✅ commit — trỏ tới `alertmanager:9093` |
| `infra/prometheus/alerts.yml` | ✅ commit — 4 rule (API down, backup cũ, ES heap/lifecycle...) |

## 3. Dựng lại trên host (CT102)

```bash
cd /opt/kg
git rev-parse --short HEAD                    # phải khớp commit đang deploy
cd ops/alertmanager
cp alertmanager.example.yml alertmanager.yml
printf '%s' '<gmail-app-password>' > smtp_password
printf '%s' '<telegram-bot-token>' > telegram_bot_token
chown root:nogroup alertmanager.yml smtp_password telegram_bot_token
chmod 0640 alertmanager.yml smtp_password telegram_bot_token   # container chạy uid nobody
cd /opt/kg
docker compose -f ops/docker-compose.ops.yml -p kgops up -d
```

- Secret dùng `printf` (không `echo`) để **không có newline cuối file**; Alertmanager đọc
  nguyên nội dung file qua `smtp_auth_password_file` / `bot_token_file`.
- Quyền `0640 root:nogroup` là mức tối thiểu đủ dùng: container chạy `uid=65534(nobody)`
  nên đọc được qua group, còn user thường trên host thì không.

- Password SMTP: Gmail **App Password** của mailbox dùng để gửi cảnh báo. Cùng giá trị
  với `MAIL_PASSWORD` trong `/opt/kg/.env` (không copy file `.env` vào `ops/`).
- Alertmanager đọc config **một lần lúc start** → sửa `alertmanager.yml` **hoặc file secret**
  phải `docker compose -p kgops restart alertmanager` (hoặc `POST /-/reload`), không tự nạp lại.
  Đổi secret trong file rồi restart là đủ, không cần đụng compose.
- **Đừng** thêm `--config.expand-env=true`: file mẫu này không cần expand, bật lên thì
  mọi `${...}` sẽ bị thay bằng biến môi trường (và muốn giữ literal phải viết `$${...}`).

## 3b. Rotate secret (không cần sửa repo)

```bash
cd /opt/kg/ops/alertmanager
printf '%s' '<new-secret>' > smtp_password        # hoặc telegram_bot_token
chown root:nogroup smtp_password && chmod 0640 smtp_password
cd /opt/kg && docker compose -f ops/docker-compose.ops.yml -p kgops restart alertmanager
docker logs --tail 3 kgops-alertmanager-1          # phải có 'Completed loading of configuration file'
```

Gmail App Password mới: myaccount.google.com → Bảo mật → Mật khẩu ứng dụng. Sau khi rotate
phải cập nhật luôn `MAIL_PASSWORD` trong `/opt/kg/.env` (app gửi mail dùng cùng App Password)
rồi deploy lại app — nếu không, alert gửi được nhưng mail của app sẽ hỏng.

## 4. Nghiệm thu (đúng thứ tự)

```bash
# 1. Prometheus thấy Alertmanager (không được 0)
curl -s localhost:9090/api/v1/alertmanagers    # cần auth/allowlist như dashboard
# 2. Rule đã nạp
docker exec kg-prometheus-1 promtool check rules /etc/prometheus/alerts.yml
# 2b. Config Alertmanager hợp lệ + đọc được secret dạng file (không in giá trị)
docker exec kgops-alertmanager-1 amtool check-config /etc/alertmanager/alertmanager.yml
docker exec kgops-alertmanager-1 sh -c 'wc -c < /etc/alertmanager/smtp_password; wc -c < /etc/alertmanager/telegram_bot_token'
# 3. Có cảnh báo thật thì thấy trạng thái + mail
docker exec kgops-alertmanager-1 amtool alert query --alertmanager.url=http://localhost:9093
docker logs kgops-alertmanager-1 2>&1 | grep -i 'Notify success'
```

Tiêu chí pass: `activeAlertmanagers: 1` và khi bắn thử alert → log có `Notify success`
+ hộp mail nhận được (đã xác nhận bằng alert thật firing/resolved).

## 5. Còn thiếu (xem handoff dev)

- Watchdog offsite hiện là script ngoài (`/usr/local/bin/kg-ops-selfcheck`, cron `*/15`)
  gửi mail trực tiếp — nó là đường **out-of-band**, không phụ thuộc Prometheus/Alertmanager.
  Khi app push được `kg_backup_last_success_timestamp` thì thêm rule trong Prometheus và
  watchdog script chỉ còn là lớp dự phòng.
- Sau mỗi lần deploy cần smoke-test entry cron backup: biến bắt buộc đổi tên/thiếu
  (từng xảy ra với `B2_KEY_ID`) sẽ làm job fail im lặng vì cron không có TTY/mail.
