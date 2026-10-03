# Reply to Proxmox ops bot — ES lifecycle control (chốt)

> Từ knowledge-gym maintainers. Bot đã đo đúng: bind `127.0.0.1` từ app container không tới được host helper.

## Trả lời 3 câu hỏi (§5 của bạn)

### 1. Repo / path

- GitHub: `https://github.com/darkb2001/knowledge-gym` (branch `main`)
- File cần khớp ngay:
  - `docker-compose.prod.yml` — service name **`elasticsearch`**
  - `.env.example`
  - `scripts/es-control.sh` (legacy; sẽ bị thay bởi HTTP agent của bạn)
- **Path trên CT102:** repo và production dùng `/opt/kg` làm path chuẩn — hardcode agent theo path thật, báo lại trong inventory để app `.env` khớp:
  - `KG_ES_CONTROL_URL=http://172.18.0.1:9377` (hoặc bridge gw thật bạn đo)
  - `KG_ES_CONTROL_TOKEN=...`
  - `KG_COMPOSE_FILE=/opt/kg/docker-compose.prod.yml` (nếu script còn dùng)

Không cần clone cả monorepo javaNote — chỉ repo `knowledge-gym`.

### 2. Transport — **chốt: HTTP trên bridge gateway + token**

Đúng như bạn khuyến nghị và đã đo:

| Bind | App container | LAN (CT101) |
|---|---|---|
| `127.0.0.1:9999` | **không tới** | — |
| bridge gw `:9377` | **tới được** | **không tới** ✅ |

**Không** dùng unix socket trừ khi sau này đổi kiến trúc.  
**Không** mount `/var/run/docker.sock` vào app.

Contract agent (đồng ý gần như nguyên §3 của bạn):

- systemd `kg-es-control.service`, user `kgctl`, listen **chỉ** bridge gw `:9377`
- Header `X-KG-Token` bắt buộc; sai/thiếu → 401
- Allowlist body/path: `status` | `start` | `stop` only; service name hardcode `elasticsearch`; compose dir hardcode
- `stop`: `docker compose stop elasticsearch` (SIGTERM, **không** kill) → chờ `exited` → ghi marker file để deploy sau không kéo ES lên lại (xem dưới) → 200
- `start`: xoá marker → `up -d elasticsearch` → poll `_cluster/health` tới **green hoặc yellow**, ưu tiên green trên single-node 0 replica; timeout 180s
- Mode gate: **app** đã `PUT` mode → `POSTGRES` trước khi gọi `stop` (code hiện tại). Agent **không bắt buộc** đọc DB; nếu muốn defense-in-depth, agent chỉ cần marker/`es.desired=stopped` — tránh coupling Postgres từ user `kgctl`.

App side (sắp sửa trong repo): `EsControlHttpAdapter` gọi `POST/GET` agent với token; ProcessBuilder script chỉ còn fallback local/dev.

### 3. `./gradlew test` trên CT102?

**Không cần** để đóng m11 ops. CI GitHub đã green trên Linux + Testcontainers.

Chỉ chạy trên CT102 nếu bạn muốn debug flaky integration — khi đó:

- Cài Temurin 21
- **Stop ES** (và ideally Kafka) trước khi test để đủ RAM 8GB
- Đừng để JVM test tranh heap với prod app đang chạy cùng CT — hoặc chạy trong giờ bảo trì / scale app down tạm

**Không** coi pass local CT102 là gate thay CI.

---

## Xác nhận các lỗ hổng bạn nêu (code sẽ sửa)

| # | Issue | Verdict | Action |
|---|---|---|---|
| (1) | Loopback bind | **Đúng** | HTTP bridge gw — bạn dựng agent; app đổi sang HTTP client |
| (2) | Pruner xoá pending | **Đúng khi skip reindex** | Pruner **chỉ** `processed_at IS NOT NULL` |
| (3) | reindex lúc ES down | **Đúng** | Skip reindex nếu ping ES fail hoặc mode=`POSTGRES` |
| (4) | `compose up -d` dựng lại ES | **Đúng** (`restart: unless-stopped` + `up -d` vẫn start service) | Agent ghi marker e.g. `/opt/kg/run/elasticsearch.stopped`; `deploy-prod.sh` / compose up tôn trọng marker (`--scale elasticsearch=0` hoặc bỏ qua service) |
| (5) | ES client timeout ngắn | **Đúng** | Set `spring.elasticsearch.connection-timeout` / `socket-timeout` ~1–2s trong yml |

Green trên single-node 0 replica: đồng ý — poll **green trước**, chấp nhận yellow chỉ nếu timeout gần hết và status ≥ yellow.

---

## Việc bot làm tiếp (go)

1. Dựng `kg-es-control.service` đúng contract trên.  
2. Ghi token vào `/opt/kg/.env` (0600), **không** paste chat.  
3. Báo lại: bridge gw IP thật, port, path compose, marker path.  
4. Chạy test §4 của bạn (stop → cgroup MB, 401/400, LAN đóng, `/search` 200 khi POSTGRES).  
5. **Chưa** cần `./gradlew test` trừ khi được yêu cầu thêm.

Maintainer sẽ push: pruner fix, reindex skip, HTTP adapter, deploy marker, ES timeouts, sửa handoff “host = CT102 không phải PVE node”.
