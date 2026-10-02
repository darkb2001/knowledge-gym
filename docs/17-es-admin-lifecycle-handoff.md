# Handoff note — Admin Elasticsearch runtime/lifecycle control

> Dùng note này làm context cho bot tiếp theo khi hoàn thiện cấu hình và deployment.

## Mục tiêu

Cho phép ADMIN chọn backend search runtime:

- `POSTGRES`: không gọi Elasticsearch, dùng PostgreSQL `tsvector + GIN`.
- `AUTO`: ưu tiên Elasticsearch, lỗi thì fallback PostgreSQL.
- `ELASTICSEARCH`: ưu tiên Elasticsearch, vẫn fallback PostgreSQL khi cluster lỗi.

Ngoài ra cần có nút lifecycle để stop/start Elasticsearch nhằm giải phóng RAM trên LXC 102.

## Đã có trong code

### ES baseline đã ship

- `APP_SEARCH_ES_ENABLED=false` mặc định.
- `GlobalSearchUseCase` có PostgreSQL fallback.
- `search_outbox` + relay + retention/pruner.
- `reindex-on-startup`.
- ES index 1 shard, 0 replica.
- Bulk refresh `WaitFor`.
- ES startup failure không làm app fail.
- Production ES private-only, không expose port host.
- ES healthcheck chờ `yellow`.

### Runtime switch đã thêm

- `kg-core/.../search/domain/port/SearchModeSettingsPort.java`
- `kg-core/.../search/application/SearchModeUseCase.java`
- `kg-infrastructure/.../search/JdbcSearchModeSettingsAdapter.java`
- `kg-infrastructure/src/main/resources/db/migration/V024__search_runtime_settings.sql`
- `GlobalSearchUseCase` đã nhận runtime mode.
- `SearchIndexRelay` không drain khi mode là `POSTGRES`.
- Admin API:
  - `GET /api/v1/admin/search/settings`
  - `PUT /api/v1/admin/search/settings`
- Admin UI:
  - `kg-frontend/app/admin/search/page.tsx`
- Host helper:
  - `scripts/es-control.sh`
  - Actions: `status`, `start`, `stop`.
- Operations doc:
  - `docs/16-search-operations.md`

## Việc bot tiếp theo phải làm

### 1. Sửa test đang fail trước tiên

Chạy đúng JDK:

```bash
cd knowledge-gym
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew test --no-daemon --stacktrace
```

Lỗi compile đã từng gặp sau khi thêm `SearchModeSettingsPort`:

```text
SearchIndexRelay constructor cannot be applied
```

Đã thêm constructor compatibility cho test cũ, nhưng phải chạy lại full test để xác nhận.

Full test trước đó bị timeout tại integration tests dùng Testcontainers. Cần kiểm tra Docker Desktop/Testcontainers daemon. Không được kết luận pass nếu chỉ compile.

### 2. Chạy migration V024 trên DB thật

Kiểm tra Flyway tạo thành công:

```sql
SELECT * FROM search_runtime_settings;
SELECT * FROM search_runtime_setting_audit;
```

Default phải là:

```text
POSTGRES
```

### 3. Hoàn thiện lifecycle API thật

Hiện `scripts/es-control.sh` chỉ là helper trên host. Chưa có backend gọi helper.

Không được mount Docker socket vào app:

```text
/var/run/docker.sock
```

Cần một host-side control agent hoặc systemd/sudoers contract:

```text
Spring API → authenticated host control endpoint → allowlisted script → docker compose
```

Chỉ cho phép:

```text
status
start
stop
```

Không nhận arbitrary command.

### 4. Lifecycle flow bắt buộc

#### Stop

1. ADMIN request stop.
2. Chuyển runtime search mode về `POSTGRES` trước.
3. Không cho relay tiếp tục gửi request ES.
4. Gọi host helper stop container.
5. Chờ xác nhận container stopped.
6. UI hiển thị mode PostgreSQL và ES stopped.

#### Start

1. Gọi host helper `docker compose up -d elasticsearch`.
2. Poll cluster health.
3. Chỉ coi là ready khi `yellow` hoặc `green`.
4. Cho phép chuyển mode sang `AUTO`/`ELASTICSEARCH`.
5. Reindex nếu index thiếu dữ liệu.

### 5. Test bắt buộc

- ADMIN đọc settings: 200.
- USER đọc/update admin endpoint: 403.
- POSTGRES mode không gọi ES.
- AUTO dùng ES khi healthy.
- AUTO fallback PostgreSQL khi ES down.
- ELASTICSEARCH fallback PostgreSQL khi timeout.
- Optimistic version conflict trả 409.
- Stop ES không làm `/search` trả 500.
- Start ES chờ yellow/green trước khi enable.
- Search outbox không retry vô hạn khi mode POSTGRES.
- Frontend lint/test.
- Full Gradle test với JDK 21 và Docker chạy thật.

## Verification commands

```bash
cd knowledge-gym
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :kg-presentation:compileJava :kg-core:test :kg-infrastructure:compileJava --no-daemon
./gradlew test --no-daemon --stacktrace

cd kg-frontend
npm run lint
npm test -- --run
```

## Acceptance criteria

Chỉ đánh dấu DONE khi thỏa cả 3 điều kiện:

1. Admin chuyển được `POSTGRES/AUTO/ELASTICSEARCH` runtime và fallback đúng.
2. Admin bấm stop/start và Elasticsearch container thật sự dừng/chạy lại qua host control an toàn.
3. Full Gradle test không còn failure/timeout chưa giải thích; migration và smoke test chạy trên PostgreSQL/Compose thật.

## Lưu ý bảo mật

- Không ghi Context7 token, DB password hoặc cron token vào note/config commit.
- Không expose Elasticsearch port ra host/public.
- Không mount Docker socket vào Spring app.
- Host control token phải nằm trong secret/env riêng.
