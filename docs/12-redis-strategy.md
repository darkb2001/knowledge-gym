# Redis Strategy — Knowledge Gym

> Redis 7 là **cốt lõi** cho performance + security trong dự án.
> Host: docker-compose `redis:7-alpine` trên server local của bạn (không dùng cloud Redis).
> Client: **Lettuce** (reactive, non-blocking) hoặc **Redisson** (distributed locks, advanced structures).

## Login Flow — Redis tham gia ở đâu?

```
POST /auth/login { email, password }
       │
       ▼
┌─ AuthService ──────────────────────────────────────────────┐
│ 1. Verify email + BCrypt(password) === password_hash        │
│ 2. Generate JWT access token (15 min, in-memory signing)     │
│ 3. Generate refresh token (random UUID, hashed SHA-256)      │
│                                                             │
│ ┌─ Redis ────────────────────────────────────────────────┐  │
│ │ SET rt:{token_hash} = "userId:familyId"  EX 604800 (7d)│  │
│ └────────────────────────────────────────────────────────┘  │
│                                                             │
│ ┌─ PostgreSQL ───────────────────────────────────────────┐  │
│ │ INSERT refresh_tokens (token_hash, family_id, user_id, │  │
│ │   ip_address, user_agent)                               │  │
│ └────────────────────────────────────────────────────────┘  │
│                                                             │
│ Response:                                                   │
│   { accessToken: "eyJhbG...", expiresIn: 900 }             │
│   + Set-Cookie: refreshToken=<raw> httpOnly; Secure;       │
│                 SameSite=Strict; Path=/api/v1/auth/refresh  │
│                 Max-Age=604800                              │
└─────────────────────────────────────────────────────────────┘

POST /auth/refresh (cookie: refreshToken=xxx)
       │
       ▼
┌─ RefreshTokenUseCase ──────────────────────────────────────┐
│ 1. hash = SHA-256(raw_token)                                │
│ 2. Redis GET rt:{hash}        ← O(1) nhanh hơn DB query     │
│    ├─ KHÔNG TỒN TẠI → 401 (expired/invalid)                │
│    └─ TỒN TẠI → userId:familyId                            │
│ 3. Redis GET rt:blacklist:{hash}                            │
│    └─ TỒN TẠI → reuse detected!                             │
│       → SET rt:revoked:{familyId} = 1 EX 604800             │
│       → DELETE rt:{hash}                                     │
│       → REVOKE family trong PostgreSQL                       │
│       → 401                                                  │
│ 4. Redis GET rt:revoked:{familyId}                          │
│    └─ TỒN TẠI → family revoked → 401                       │
│ 5. VALID → generate token mới:                               │
│    SET rt:{new_hash} = "userId:familyId" EX 604800          │
│    SET rt:blacklist:{old_hash} = 1 EX (còn lại TTL)         │
│    INSERT PostgreSQL audit log                               │
│    → Response { accessToken } + Set-Cookie new refresh       │
└─────────────────────────────────────────────────────────────┘
```

## Tất cả Redis Flows trong dự án

### 1. Auth — Refresh Token Rotation (mini-phase 03)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `rt:{token_hash}` | `userId:familyId` | 7d | SET / GET / DEL |
| `rt:blacklist:{token_hash}` | `1` | 7d | SET / EXISTS |
| `rt:revoked:{familyId}` | `1` | 7d | SET / EXISTS |

**Purpose:** Lookup O(1), reuse detection, family revocation. Không cần query PostgreSQL mỗi lần refresh.

### 2. Forgot Password OTP (mini-phase 03)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `pwd_reset:{email}` | code hash SHA-256 | 10m | SET / GET / DEL |

**Purpose:** Cache OTP code, auto-expire. Backup trong DB (`password_reset_codes`).

### 3. Rate Limiting — Bucket4j (mini-phase 03)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `rl:login:{ip}` | counter (token bucket) | 60s | EVAL (Lua script) |
| `rl:register:{ip}` | counter | 1h | EVAL |
| `rl:forgot:{ip}` | counter | 60s | EVAL |
| `rl:global:{ip}` | counter | 60s | EVAL |

**Purpose:** Distributed rate limit across instances. Bucket4j dùng Redis backend (Lua atomic script).

### 4. Entity Cache — Caffeine L1 (mini-phase 04, m4a)

**m4a dùng Caffeine L1-only — KHÔNG có Redis L2 cho content cache.** Nội dung câu hỏi là tĩnh,
đọc nhiều/ghi rất ít; mất cache khi restart là chấp nhận được, và L1 tránh round-trip mạng cho
endpoint đọc nhiều nhất. (`kg-infrastructure` `CacheConfig` + `kg-presentation` `CachingConfig`.)

| Spring cache name | Kiểu | Cấu hình | Endpoint dùng |
|---|---|---|---|
| `questions` | Caffeine L1 | `maximumSize=1000`, `expireAfterWrite=30m` | `@Cacheable("questions", key="#id")` → `GET /questions/{id}` |
| `topics` | Caffeine L1 | `maximumSize=1000`, `expireAfterWrite=30m` | `@Cacheable("topics", key="'all'")` → `GET /topics` |
| `modules` | Caffeine L1 | `maximumSize=1000`, `expireAfterWrite=30m` | (dự phòng; `GET /modules` chưa cache) |

Config: `app.cache.questions.max-size` (default `1000`), `app.cache.questions.ttl` (default `30m`).

**Eviction:** mọi mutation admin (`POST|PATCH|DELETE /admin/content/questions...`) evict **cả 3 cache**
(`@CacheEvict(value={"questions","topics","modules"}, allEntries=true)`) — `topics`/`modules` chứa
`moduleCount`/`questionCount` phái sinh nên thêm/xoá 1 câu hỏi làm số đếm trong 2 cache kia sai ngay.
Import async (`POST /admin/content/parse`) clear cả 3 trong `finally` sau khi job xong, **kể cả khi lỗi
giữa chừng** (import không transactional → rows đã commit vẫn nằm trong DB).

> **Redis L2 = scale-out tương lai, chưa implement.** Nếu sau này chạy nhiều replica và cần cache chia sẻ,
> thêm Redis L2 (vd `cache:q:{id}`, `cache:m:{id}`, `cache:t:{id}`, TTL 30–60m) và chuyển sang
> `RedisCacheManager` composite L1+L2. Hiện tại đây **không phải** hành vi đang chạy.

### 5. Leaderboard — Sorted Set (m7, shipped)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `lb:global` | sorted set (score = XP) | 1h | ZREVRANGE / ZRANGE |
| `lb:global:tmp:{uuid}` | sorted set tạm khi rebuild (**uuid riêng mỗi lần**) | 60s (phòng rebuild chết giữa chừng) | ZADD / EXPIRE / RENAME |
| `lb:global:empty` | marker leaderboard rỗng | 5m | SET NX / EXISTS |

**Purpose:** BXH top 100 users. Sorted set cho O(log N) rank lookup.
`ZADD lb:global:tmp:<uuid> 1500 "0000001500:user-42"` → `ZREVRANGE lb:global 0 99` → top 100.

**Nguồn điểm = `users.xp`** (read model ghi cùng tx với attempt; recompute được từ `study_attempts`
qua công thức ở `07-erd.md` quy tắc "XP / streak"). Rebuild **không** quét `study_attempts` mỗi giờ.
Chỉ user có `xp > 0` vào BXH (`WHERE xp > 0`) — user mới chưa học không chiếm top 100.

**Cache-aside rebuild (bắt buộc làm đúng):**

```
GET top100:
  ZREVRANGE lb:global 0 99 WITHSCORES
  ├─ miss → ZADD lb:global:tmp:<uuid> <score> <member> ...  (từ users.xp: WHERE xp > 0 ORDER BY xp DESC LIMIT 100)
  │         EXPIRE lb:global:tmp:<uuid> 60
  │         RENAME lb:global:tmp:<uuid> lb:global   ← atomic, reader không thấy set nửa vời
  │         EXPIRE lb:global 3600
  ├─ miss + DB rỗng → SET lb:global:empty 1 NX EX 300; trả [] (tránh rebuild stampede)
  └─ hit   → dùng luôn
```

- **Key tạm phải unique mỗi lần rebuild.** Hai request cùng miss mà cùng ghi `lb:global:tmp` thì `RENAME`
  thứ hai nổ `ERR no such key` (key đã bị rename). Dùng `tmp:<uuid>` hoặc cờ rebuild có TTL.
- **Set rỗng ≠ miss.** Adapter kiểm tra `lb:global:empty` trong 5 phút để tránh rebuild khi DB chưa có
  user nào có XP; lần rebuild có dữ liệu sẽ xóa marker.
- **Không** chỉ `ZADD` lên key cũ khi rebuild: member đã tụt khỏi top 100 hoặc user bị xoá sẽ nằm
  lại vĩnh viễn, và `EXPIRE` trên key có data cũ làm sai thứ tự. Rebuild luôn ghi vào key tạm rồi `RENAME`.
- **Tie-break tất định**: `ZADD` chỉ có 1 score nên user cùng XP được sắp theo member (lexicographic).
  Encode member = `String.format("%010d:%s", xp, userId)` (score = `xp`) — **một** cách duy nhất, không
  thêm phương án "score phụ lẻ". `userId` parse lại từ member để lấy displayName (1 query batch, no N+1).
- **`lb:topic:{topicId}` chưa ship** (đã bỏ khỏi m7): không tồn tại XP per-topic ở DB. Chỉ thêm khi có
  dữ liệu thật (m12+), lúc đó phải định nghĩa rõ nguồn điểm.

### 6. User Daily Streak / Study Stats (mini-phase 07) — **KHÔNG ship ở m7, chỉ là phương án tương lai**

> **⚠️ m7 KHÔNG tạo 3 key dưới đây.** Bảng giữ lại làm tham chiếu cho tương lai khi streak được cache.
> Streak ở m7 tính **on-read** từ `study_attempts` (1 user, vài trăm row, endpoint không phải hot path)
> ⇒ không cần cache, và cũng không cần `INCR` `attempts:*`.

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `streak:{userId}:current` | INT (số ngày liên tiếp) | 1h | GET / SET |
| `streak:{userId}:last_date` | DATE string | 1h | GET / SET |
| `attempts:{userId}:{date}` | INT (số attempts hôm nay) | 48h | INCR |

> Hệ quả cho key space: leaderboard ghi `lb:global`, marker rỗng `lb:global:empty` và key tạm
> `lb:global:tmp:<uuid>` trong lúc rebuild.
> Bảng trên giữ lại như *phương án* nếu sau này đo thấy chậm; đừng đọc nó như hành vi đang chạy.
> Hệ quả cho key space: chỉ các key leaderboard nêu trên được ghi ở m7; streak tính trực tiếp từ PostgreSQL.

**Streak không phải state, và ở m7 cũng không phải cache.** Nguồn gốc duy nhất: `study_attempts`
(số ngày liên tiếp có ≥1 attempt, bucket theo `(attempted_at AT TIME ZONE :zone)::date`, zone =
`app.progress.timezone`). `user_progress.streak_days` tồn tại (V004) nhưng **m7 không ghi** — streak
per-module sẽ cần quét lịch sử mỗi lần review, tức thêm query vào hot path mà `ON CONFLICT` cố tình
gộp lại. Nếu sau này cache streak: mọi key **phải** có TTL (bản cũ ghi "persistent (no TTL)" — mâu thuẫn
với quy tắc "Redis chỉ là cache TTL 1h" ở `07-erd.md`, và Redis restart/FLUSHALL sẽ thành mất state
nếu không TTL mà vẫn coi là nguồn thật). Miss ⇒ rebuild từ PG, không `INCR` làm state.

### 7. AI User Quota (mini-phase 10)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `ai:{userId}:{yyyyMMdd}` | INT (số lần dùng AI hôm nay) | 48h | INCR / GET |

**Purpose:** Giới hạn 50 requests/ngày/user. `INCR ai:42:20260926` → nếu > 50 → deny.
TTL 48h để cover múi giờ khác nhau.

### 8. Blog View Counter (mini-phase 09)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `views:{postId}` | INT | 5m | INCR |
| `likes:{postId}` | INT | 5m | INCR |

**Purpose:** Buffer view/like counting trong Redis, flush xuống PostgreSQL mỗi 5 phút (giảm write I/O).

### 9. Distributed Lock — Quiz Submission (mini-phase 06)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `lock:quiz:{sessionId}:{userId}` | UUID owner token | 10s | SET NX / Lua compare-and-delete |

`RedisLockAdapter` dùng `StringRedisTemplate`: set-if-absent với TTL và nhả lock bằng Lua chỉ khi
token còn khớp. Redis lỗi → **fail-open**, kể cả nhả lock lỗi cũng không che lỗi gốc của submit.

Lock chỉ giảm request bấm đúp, không giữ tính đúng đắn. Transaction submit khoá row session bằng
`SELECT ... FOR UPDATE` (scope user), kiểm tra `finished_at`, insert answers bằng
`ON CONFLICT (session_id, question_id) DO NOTHING` trên UK V016, cập nhật kết quả và ghi PRACTICE
trong cùng transaction. Request thứ hai → 409, không ghi thêm attempt. Nhả Redis trước commit
vẫn an toàn vì DB row lock giữ đến commit. Test M6 dừng Redis và submit đồng thời để chứng minh.

### 10. Distributed Lock — AI Writer Run (mini-phase 10)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `lock:ai:writer` | UUID (owner) | 120s | SET NX EX |

**Purpose:** Chỉ cho 1 AI writer instance chạy tại 1 thời điểm (tránh duplicate posts).

### 11. Online User Count (mini-phase 12)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `online:{userId}` | `1` | 5m | SET EX |
| `online:count` | INT (HyperLogLog hoặc SET cardinality) | — | PFADD / PFCOUNT |

**Purpose:** Dashboard hiển thị "X users đang online".

## Tổng hợp Redis Key Space

```
┌─────────────────────────────────────────────────────────────────────┐
│  Redis 7 — Knowledge Gym Key Space                                  │
│                                                                      │
│  AUTH (m03)          │  CACHE (future)        │  RATE LIMIT (m03)    │
│  rt:{hash}    → 7d   │  (m4a dùng Caffeine L1 │  rl:login:{ip}      │
│  rt:bl:{hash} → 7d   │   in-JVM, KHÔNG Redis) │  rl:register:{ip}  │
│  rt:rev:{fid} → 7d   │  Redis L2 khi scale-out│  rl:forgot:{ip}    │
│  pwd:{email}  → 10m  │                         │  rl:global:{ip}    │
│──────────────────────┼─────────────────────────┼────────────────────│
│  LEADERBOARD (m07)   │  STREAK (m07)           │  AI QUOTA (m10)    │
│  lb:global    → 1h   │  KHÔNG cache ở m7:      │  ai:{uid}:{date}   │
│  lb:global:tmp:      │  streak tính on-read    │  → 48h             │
│    {uuid}     → 60s  │  từ study_attempts;     │                    │
│  ↑ cache của users.xp│  key streak:{uid}:* chỉ │                    │
│                      │  là phương án tương lai │                    │
│──────────────────────┼─────────────────────────┼────────────────────│
│  BLOG (m09)          │  LOCKS (m06,m10)        │  ONLINE (m12)      │
│  views:{pid}  → 5m   │  lock:quiz:{sid}:{uid}  │  online:{uid} → 5m │
│  likes:{pid}  → 5m   │  lock:ai:writer → 120s  │  online:count (HLL)│
└─────────────────────────────────────────────────────────────────────┘

Estimated memory (1000 users):
  Auth tokens:     ~50 KB  (2000 keys × 100 bytes)
  Rate limit:      ~20 KB  (100 keys × 200 bytes)
  Leaderboard:     ~100 KB (1 sorted set × 1000 members)
  Streak/stats:    0 KB    (m7 KHÔNG cache streak — tính on-read từ PG)
  ↑ nếu sau này cache streak: ~200 KB (3000 keys × 60 bytes), TTL bắt buộc
  AI quota:        ~50 KB  (1000 keys × 50 bytes)
  Blog counters:   ~10 KB  (50 keys × 200 bytes)
  Locks:           ~5 KB   (10 keys × 500 bytes)
  Online:          ~50 KB  (1000 keys × 50 bytes)
  TOTAL:           ~0.5 MB  ← chưa tính content cache (m4a dùng Caffeine in-JVM, không ở Redis)
```

## Docker Compose

```yaml
  redis:
    image: redis:7-alpine
    command: >
      redis-server
      --maxmemory 128mb
      --maxmemory-policy allkeys-lru
      --save 60 1000
      --appendonly yes
      --appendfsync everysec
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
    ports:
      - "6379:6379"
```
