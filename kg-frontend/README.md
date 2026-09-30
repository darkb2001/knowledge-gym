# Knowledge Gym Frontend (m4b)

Next.js 14 App Router + TypeScript + Tailwind. Nói chuyện với backend qua
`NEXT_PUBLIC_API_BASE` (mặc định `http://localhost:8080/api/v1`).

## Chạy local

```bash
# Terminal 1 — API (cần Postgres/Redis theo docker-compose)
cd knowledge-gym && ./gradlew :kg-presentation:bootRun

# Terminal 2 — FE
cd knowledge-gym/kg-frontend
cp .env.example .env.local   # chỉnh nếu cần
npm run dev                  # http://localhost:3000
```

## Auth

| Route | Việc |
|---|---|
| `/login` | email/password + nút Google |
| `/register` | tạo tài khoản |
| `/forgot-password` | 3 bước: email → mã 6 số → mật khẩu mới |
| `/auth/oauth2/success` | đọc `#accessToken=…` từ OAuth redirect |

- Access JWT: **in-memory** (`lib/api-client.ts`) — không localStorage.
- Refresh: httpOnly cookie, `credentials: "include"`, tự refresh khi 401 / reload.
- Google: `window.location = ${API_BASE}/oauth2/authorization/google`.

## Questions

| Route | Việc |
|---|---|
| `/questions` | filter module / difficulty / tag + search `q` + phân trang |
| `/questions/[id]` | render `answerHtml` (đã sanitize phía server) |

CORS backend đã allow `http://localhost:3000` + credentials.
