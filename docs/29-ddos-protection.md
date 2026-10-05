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
| `api.darkb-tech.io.vn` | Cloudflare proxy + tunnel | CF L3/L4 unmetered, WAF free (UA `sqlmap` → **403**), **CF rate limit 250 req/10s/IP** (edge, có `error code: 1015`), nginx `limit_req` auth 10r/s + api 60r/s, Bucket4j 5 login/phút/IP | 120 req dồn vào `/auth` → **87×503**; 1000 req vào `/api/v1/topics` → **16×429** (edge/app shed) |
| `9router.darkb-tech.io.vn` | Cloudflare proxy | CF; **cố ý không rate-limit** (đây là LLM gateway của agent) | `/v1/chat/completions` không key → **401** |
| `app.darkb-tech.io.vn` (FE) | **Vercel trực tiếp, KHÔNG qua Cloudflare** | Vercel DDoS mitigation (mọi gói) + WAF: **Challenge Bot Protection + Deny AI Bots** (chủ web bật 05/10) | 1 lượt xem ≈ **745 KB** ⇒ quota Free 100 GB ≈ **137k lượt/tháng** |

**Điểm yếu còn lại:** (a) FE không qua Cloudflare nên không có WAF/rate-limit rule
của zone, chỉ có Vercel (đã bật bot challenge) + hạn mức quota; (b) trần thật của
API là băng thông upload của mạng nhà (tunnel) — CF phải chặn trước khi tới đó;
(c) gói Free chỉ cho 1 rate-limit rule nên `/auth` không có rule riêng ở edge.

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
ops/cloudflare/apply-ddos-rules.sh                 # 1 rate limit rule
ops/cloudflare/apply-ddos-rules.sh --verify 400    # bắn 400 req, kiểm tra CF chặn ở edge
ops/cloudflare/apply-ddos-rules.sh --under-attack on    # kill-switch
ops/cloudflare/apply-ddos-rules.sh --under-attack off
```
- Rule đang chạy: `/api/*` → **250 req/10s/IP → block 10s** (chặn flood API trước
  khi tốn băng thông tunnel). Ngưỡng ~25 req/s: đủ rộng cho NAT dùng chung (CGNAT),
  đủ chặt để cắt flood; hạ `requests_per_period` nếu muốn chặt hơn.
- **Gói Free — giới hạn đã kiểm chứng bằng API (05/10), sai là API trả 400:**
  1. mỗi zone **chỉ 1** rate-limit rule ⇒ không thể vừa có rule riêng cho `/auth`
     vừa có rule cho `/api`; brute-force `/auth` vẫn được app (Bucket4j 5 login/phút)
     + nginx (zone `auth` 10 r/s) lo;
  2. expression **chỉ được dùng field `Path`** (`http.request.uri.path`). Bản dùng
     `http.host eq ...` được API nhận nhưng **không bao giờ kích hoạt** — đã test
     250 request dồn mà không thấy chặn;
  3. `characteristics` **bắt buộc có `cf.colo.id`** (không có ⇒ lỗi 20155);
  4. `period` chỉ được **10**, `mitigation_timeout` chỉ được **10**.
- **Kết quả nghiệm thu (05/10):** hạ ngưỡng tạm về 5 req/10s → bắn 60 request
  (~33 req/s) → **31 request bị chặn ở edge**, body `error code: 1015`; sau đó
  khôi phục 250/10s.
- **Đếm theo colocation**: counter tách theo `cf.colo.id`, một flood trải nhiều
  colo (SIN/HKG/NRT) sẽ bị chặn từng phần — đúng thiết kế của gói Free.
- Rule mới có **độ trễ lan truyền ở edge** (hàng chục giây): sửa rule xong test
  ngay sẽ thấy như "không có tác dụng"; đợi ~1–2 phút rồi hãy đo.
- Script `PUT` cả phase ⇒ rule tạo tay trên dashboard sẽ bị xoá.
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
| ~~Chạy `apply-ddos-rules.sh`~~ | **xong 05/10** | `CF_API_TOKEN` đã có trong `/root/.hermes/.env` (0600); rule `250 req/10s` đang live, đã nghiệm thu thấy `error code: 1015` |
| Bật Cloudflare Free Managed Ruleset + Bot Fight Mode | dashboard CF | API `PATCH settings/bot_fight_mode` trả **403** với token hiện tại ⇒ phải bật tay trên dashboard |
| ~~Vercel: bật bot protection~~ | **xong 05/10** | chủ web đã bật Challenge Bot Protection + Deny AI Bots |
| Turnstile cho đăng ký/quên mật khẩu/xác minh email | **code xong 05/10** (`sha-829e62c`) | còn gắn `APP_TURNSTILE_ENABLED=true` + `APP_TURNSTILE_SECRET` vào `/opt/kg/.env` rồi recreate app — xem `docs/30-turnstile.md` |
| (Tuỳ chọn) cho FE đi qua Cloudflare | quyết định của chủ web | đổi DNS `app.` sang proxied: được WAF/rate limit cho FE, đổi lại mất vài tối ưu edge Vercel |
| Cảnh báo quota Vercel (FDT > 50 GB/tháng) | dashboard Vercel | Hobby không expose API usage qua token thường |

## 5. Tiêu chí nghiệm thu (đo được, không cảm tính)

1. `docker exec kg-nginx-1 nginx -T | grep -c 'limit_conn conn_per_ip'` ≥ 1 và
   `nginx -t` sạch sau deploy.
2. Bắn 120 request dồn vào `/api/v1/auth/...`: **≥ 70% trả 429** (trước đây 503).
3. Bắn 1000 request dồn vào `/api/v1/topics`: `shed_rate > 20 req/s` xuất hiện
   trong Prometheus ⇒ alert `KnowledgeGymEdgeRejectionStorm` chuyển `firing`
   trong ≤ 4 phút và **Telegram nhận được tin**.
4. Sau khi chạy script CF: bắn **> 250 request trong 10s** từ 1 IP vào `/api/`
   ⇒ một phần response trả **429 với body `error code: 1015`** (chặn ở edge, đến
   trước nginx); phải đợi ~1–2 phút sau khi sửa rule để edge lan truyền.
5. `promtool check rules /etc/prometheus/alerts.yml` → 11 rules, 0 lỗi.

## 6. Ghi chú vận hành

- Sau mỗi deploy phải `--force-recreate` **nginx** và **prometheus**: bind-mount
  file bám inode, `git reset --hard` tạo inode mới nên container cũ vẫn đọc bản
  cũ (CI đã thêm bước này — xem `.github/workflows/ci.yml`).
- Đừng đặt rate limit rule lên host `9router` — agent dùng nó làm LLM gateway,
  chặn là tự bắn vào chân mình.
- `nginx_http_requests_total` reset khi reload nginx (stub_status) — `rate()`
  đã xử lý counter reset, nhưng đừng ngạc nhiên khi thấy đồ thị răng cưa lúc deploy.
