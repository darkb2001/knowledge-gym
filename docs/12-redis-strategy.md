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

### 5. Leaderboard — Sorted Set (mini-phase 07)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `lb:global` | sorted set (score = XP) | 1h (refresh) | ZADD / ZREVRANGE / ZREVRANK |
| `lb:topic:{topicId}` | sorted set | 1h | ZADD / ZREVRANGE |

**Purpose:** BXH top 100 users. Sorted set cho O(log N) insert + O(1) rank lookup.
`ZADD lb:global 1500 user:42` → `ZREVRANGE lb:global 0 99` → top 100.

### 6. User Daily Streak / Study Stats (mini-phase 07)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `streak:{userId}:current` | INT (số ngày liên tiếp) | persistent (no TTL) | INCR / GET / SET |
| `streak:{userId}:last_date` | DATE string | persistent | GET / SET |
| `attempts:{userId}:{date}` | INT (số attempts hôm nay) | 48h | INCR |

**Purpose:** Streak tracking nhanh, không cần query PostgreSQL mỗi lần user trả lời câu hỏi.

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
│  lb:global    → 1h   │  streak:{uid}:cur       │  ai:{uid}:{date}   │
│  lb:topic:{id}→ 1h   │  streak:{uid}:last      │  → 48h             │
│                       │  attempts:{uid}:{date}  │                    │
│──────────────────────┼─────────────────────────┼────────────────────│
│  BLOG (m09)          │  LOCKS (m06,m10)        │  ONLINE (m12)      │
│  views:{pid}  → 5m   │  lock:quiz:{sid}:{uid}  │  online:{uid} → 5m │
│  likes:{pid}  → 5m   │  lock:ai:writer → 120s  │  online:count (HLL)│
└─────────────────────────────────────────────────────────────────────┘

Estimated memory (1000 users):
  Auth tokens:     ~50 KB  (2000 keys × 100 bytes)
  Rate limit:      ~20 KB  (100 keys × 200 bytes)
  Leaderboard:     ~100 KB (1 sorted set × 1000 members)
  Streak/stats:    ~200 KB (3000 keys × 60 bytes)
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