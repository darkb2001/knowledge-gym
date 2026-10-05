# 29 — Chống DDoS / lạm dụng (edge + app)

> Trạng thái: **đã triển khai lớp đo & chặn trong repo (05/10)**. Phần cần token
> Cloudflare/Vercel thì có script + runbook dưới đây, chạy được trong 5 phút.
> Số liệu trong tài liệu này là đo thật, không phải suy đoán.

## 1. Mô hình phòng thủ (và điểm yếu từng lớp)

```
khách → Cloudflare edge (L3/L4 unmetered, WAF free, bot fight)
      → Cloudflare Tunnel (cloudflared trên CT102, origin KHÔNG mở port)
      → nginx (rate limit theo IP thật, limit_conn)  ← kg-nginx-1, chỉ 127.0.0.1:80
      → app Spring (Bucket4j + Redis: 5 login/phút/IP ...)
```

| Host | Đường vào | Bảo vệ hiện có | Đo được (05/10) |
|---|---|---|---|
| `api.darkb-tech.io.vn` | Cloudflare proxy + tunnel | CF L3/L4 unmetered, WAF free (UA `sqlmap` → **403**), nginx `limit_req` auth 10r/s + api 60r/s, Bucket4j 5 login/phút/IP | 120 req dồn vào `/auth` → **87×503**; 1000 req vào `/api/v1/topics` → chỉ 54×503 |
| `9router.darkb-tech.io.vn` | Cloudflare proxy | CF; **cố ý không rate-limit** (đây là LLM gateway của agent) | `/v1/chat/completions` không key → **401** |
| `app.darkb-tech.io.vn` (FE) | **Vercel trực tiếp, KHÔNG qua Cloudflare** | Vercel DDoS mitigation (mọi gói) + firewall free rules; chưa có rate limit rule | 1 lượt xem ≈ **745 KB** ⇒ quota Free 100 GB ≈ **137k lượt/tháng** |

**Điểm yếu đã biết:** (a) zone Cloudflare chưa có rate limiting rule — 1 IP bắn
100 req/s thì ~95% vẫn vào tới CT102; (b) FE không qua Cloudflare nên không có
WAF/bot rule cho nó, chỉ có Vercel + hạn mức quota; (c) trần thật của API là
băng thông upload của mạng nhà (tunnel) — CF phải chặn trước khi tới đó.

## 2. Đã triển khai trong repo

### nginx (`infra/nginx.prod.conf`)
- `limit_conn_zone` + `limit_conn conn_per_ip 60` — chặn **connection flood /
  slowloris (R.U.D.Y)**: mỗi kết nối chỉ gửi 1 request rất chậm, thứ mà
  `limit_req` theo request không thấy.
- `client_header_timeout 10s`, `send_timeout 20s`, `keepalive_timeout 20s` —
  không giữ slot cho kết nối nửa vời.
- `limit_req_status 429` / `limit_conn_status 429` — trước đây bị chặn trả 503,
  dễ bị hiểu là "server lỗi" (và FE có thể retry làm nặng thêm). 429 = "đừng gửi nữa".
- `server_tokens off` — không lộ phiên bản nginx.
- Kill-switch IP: `include /etc/nginx/blocklist/*.conf` (mount `./run/blocklist`).

### Prometheus (`infra/prometheus/alerts.yml`, group `knowledge-gym-edge-ddos`)
7 rule mới, ngưỡng tính từ baseline ~5 req/s đo được:

| Alert | Điều kiện | Severity |
|---|---|---|
| `KnowledgeGymTrafficSurge` | edge > 40 req/s (5m) | warning |
| `KnowledgeGymTrafficFlood` | edge > 150 req/s (2m) | **critical** |
| `KnowledgeGymEdgeRejections` | edge chặn > 3 req/s (5m) | warning |
| `KnowledgeGymEdgeRejectionStorm` | edge chặn > 20 req/s (2m) | **critical** |
| `KnowledgeGymConnectionFlood` | `nginx_connections_active > 60` (3m) | warning |
| `KnowledgeGymAuthRateLimited` | app trả 429 > 0.5 req/s (5m) | warning |
| `KnowledgeGymNginxExporterDown` | `up{job="nginx"} == 0` (5m) | warning |

**Cách đo "đang bị đánh" mà không cần thêm exporter:**

```
shed_rate = clamp_min(
   sum(rate(nginx_http_requests_total[5m]))          # mọi request tới edge
 - sum(rate(http_server_requests_seconds_count[5m])) # request lọt tới app
, 0)
```

Request bị chặn ở nginx (429/444/blocklist) không bao giờ tới app ⇒ hiệu này
chính là số request edge đã hạ. Lưu ý `nginx_http_requests_total` **không có
nhãn `status`** (stub_status), nên muốn biết mã 4xx/5xx chi tiết ở edge thì phải
thêm log exporter (mtail/vector) — chưa cần.

### Alertmanager (`ops/alertmanager/`)
- Receiver `ops-critical` = **Telegram + email** cho `severity="critical"`
  (biết ngay trên điện thoại kể cả khi web đang bị đánh), warning vẫn chỉ email.
- Token bot + chat id nằm trong `ops/alertmanager/alertmanager.yml` trên host
  (`chmod 0640`, gitignore) — xem `docs/23-alertmanager-ops.md`.

### Script áp rule Cloudflare (`ops/cloudflare/apply-ddos-rules.sh`)
```bash
export CF_API_TOKEN='<token: Zone→WAF→Edit, Zone→Zone Settings→Edit>'
ops/cloudflare/apply-ddos-rules.sh                 # 2 rate limit rule
ops/cloudflare/apply-ddos-rules.sh --under-attack on    # kill-switch
ops/cloudflare/apply-ddos-rules.sh --under-attack off
```
- `/api/v1/auth/*`: **60 req/phút/IP → managed challenge** (chặn brute force).
- `/api/*`: **200 req/10s/IP → block 60s** (chặn flood API).
- Gói Free chỉ khớp được `Path` (+Verified Bot) và đếm theo IP ⇒ 2 rule này nằm
  đúng giới hạn đó. Script `PUT` cả phase nên rule tạo tay trên dashboard sẽ bị xoá.
- **Không** rate-limit host `9router` (agent đang dùng nó làm LLM gateway).

## 3. Runbook khi đang bị tấn công (theo thứ tự)

```bash
# 0. Xác nhận có thật hay chỉ là spike bình thường (xem 2 chỉ số này ở Grafana
#    dashboard "Knowledge Gym — Nginx gateway" hoặc Prometheus).
#    shed_rate > 20 req/s + rps > 150 ⇒ đang bị đánh.

# 1. Cloudflare — chặn tại edge (hiệu quả nhất, không tốn băng thông nhà):
ops/cloudflare/apply-ddos-rules.sh --under-attack on      # challenge mọi khách
#    hoặc nâng mức:  --security-level high

# 2. Nếu attacker lộ IP cụ thể (xem log dưới), chặn thẳng ở nginx:
ssh <CT102> "echo 'deny 203.0.113.7;' | sudo tee -a /opt/kg/run/blocklist/attack.conf"
ssh <CT102> "docker exec kg-nginx-1 nginx -t && docker exec kg-nginx-1 nginx -s reload"
#    IP thật nằm ở cột đầu access log (nginx đã real_ip từ CF-Connecting-IP):
ssh <CT102> "docker logs --since 5m kg-nginx-1 2>&1 | awk '{print \$1}' | sort | uniq -c | sort -rn | head"

# 3. FE bị đánh vào quota (Vercel): dashboard Vercel → Firewall → Attack Challenge
#    Mode; kiểm tra Usage → Fast Data Transfer.

# 4. Xong cơn: --under-attack off, xoá file blocklist, reload nginx.
ssh <CT102> "rm -f /opt/kg/run/blocklist/attack.conf && docker exec kg-nginx-1 nginx -s reload"
```

## 4. Còn lại (cần người/token)

| Việc | Ai làm | Vì sao chưa xong |
|---|---|---|
| Chạy `apply-ddos-rules.sh` | cần CF token | Repo chỉ có `GITHUB_TOKEN`/`PVE_*`; token CF chưa có trong `/root/.hermes/.env` |
| Bật Cloudflare Free Managed Ruleset + Bot Fight Mode | dashboard CF | API không chắc theo gói |
| Vercel: rate limit rule cho `/login`, biết chỗ bật Attack Challenge Mode | dashboard Vercel | cần tài khoản Vercel của chủ web |
| Turnstile cho `/login` + `/register` | dev (FE widget + BE verify) | cần sitekey/secret key từ CF trước |
| (Tuỳ chọn) cho FE đi qua Cloudflare | quyết định của chủ web | đổi DNS `app.` sang proxied: được WAF/rate limit cho FE, đổi lại mất vài tối ưu edge Vercel |
| Cảnh báo quota Vercel (FDT > 50 GB/tháng) | dashboard Vercel | Hobby không expose API usage qua token thường |

## 5. Tiêu chí nghiệm thu (đo được, không cảm tính)

1. `docker exec kg-nginx-1 nginx -T | grep -c 'limit_conn conn_per_ip'` ≥ 1 và
   `nginx -t` sạch sau deploy.
2. Bắn 120 request dồn vào `/api/v1/auth/...`: **≥ 70% trả 429** (trước đây 503).
3. Bắn 1000 request dồn vào `/api/v1/topics`: `shed_rate > 20 req/s` xuất hiện
   trong Prometheus ⇒ alert `KnowledgeGymEdgeRejectionStorm` chuyển `firing`
   trong ≤ 4 phút và **Telegram nhận được tin**.
4. Sau khi chạy script CF: bắn 300 req/10s từ 1 IP vào `/api/` ⇒ response có
   `cf-mitigated: block` (hoặc challenge) từ **edge**, `shed_rate` ở nginx ≈ 0.
5. `promtool check rules /etc/prometheus/alerts.yml` → 11 rules, 0 lỗi.

## 6. Ghi chú vận hành

- Sau mỗi deploy phải `--force-recreate` **nginx** và **prometheus**: bind-mount
  file bám inode, `git reset --hard` tạo inode mới nên container cũ vẫn đọc bản
  cũ (CI đã thêm bước này — xem `.github/workflows/ci.yml`).
- Đừng đặt rate limit rule lên host `9router` — agent dùng nó làm LLM gateway,
  chặn là tự bắn vào chân mình.
- `nginx_http_requests_total` reset khi reload nginx (stub_status) — `rate()`
  đã xử lý counter reset, nhưng đừng ngạc nhiên khi thấy đồ thị răng cưa lúc deploy.
