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

### 4. Entity Cache — L2 (mini-phase 04)

| Pattern | Value | TTL | Command |
|---|---|---|---|
| `cache:question:{id}` | JSON | 30m | SET / GET |
| `cache:module:{id}` | JSON | 30m | SET / GET |
| `cache:topic:{id}` | JSON | 60m | SET / GET |

**Purpose:** Caffeine là L1 (JVM heap, ~1s TTL cho hot keys), Redis là L2 (shared across instances).
Spring Cache: `@Cacheable("questions")` → L1 miss → L2 (Redis) → miss → DB query.

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
| `lock:quiz:{sessionId}:{userId}` | UUID (owner) | 30s | SET NX EX / Lua compare-and-delete |

**Purpose:** Tránh double-submit quiz khi user click 2 lần (debounce distributed).

```java
// Redisson hoặc Lettuce manual
String lockKey = "lock:quiz:" + sessionId + ":" + userId;
String ownerId = UUID.randomUUID().toString();
boolean acquired = redis.set(lockKey, ownerId, SetArgs.Builder.nx().ex(30));
if (!acquired) throw new QuizAlreadySubmittedException();

try {
    return quizUseCase.submit(sessionId, userId, answers);
} finally {
    // Lua script compare-and-delete (tránh xóa nhầm lock của request khác)
    redis.eval(UNLOCK_SCRIPT, List.of(lockKey), List.of(ownerId));
}
```

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
│  AUTH (m03)          │  CACHE (m04)           │  RATE LIMIT (m03)    │
│  rt:{hash}    → 7d   │  cache:q:{id}  → 30m   │  rl:login:{ip}      │
│  rt:bl:{hash} → 7d   │  cache:m:{id}  → 30m   │  rl:register:{ip}  │
│  rt:rev:{fid} → 7d   │  cache:t:{id}  → 60m   │  rl:forgot:{ip}    │
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
  Cache:           ~5 MB   (500 keys × 10 KB avg)
  Rate limit:      ~20 KB  (100 keys × 200 bytes)
  Leaderboard:     ~100 KB (1 sorted set × 1000 members)
  Streak/stats:    ~200 KB (3000 keys × 60 bytes)
  AI quota:        ~50 KB  (1000 keys × 50 bytes)
  Blog counters:   ~10 KB  (50 keys × 200 bytes)
  Locks:           ~5 KB   (10 keys × 500 bytes)
  Online:          ~50 KB  (1000 keys × 50 bytes)
  TOTAL:           ~6 MB   ← Redis 256 MB trên Docker là dư
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