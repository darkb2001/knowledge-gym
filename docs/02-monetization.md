# Monetization & Multi-tenant — DEFERRED Phase 5+

> ⚠️ **TRẠNG THÁI: DEFERRED — KHÔNG có trong schema MVP.**
>
> Toàn bộ nội dung file này là **định hướng sản phẩm tương lai**, chưa implement.
> **ADR-002** chốt MVP là **single-tenant** — ERD (`07-erd.md`) **không** có bảng
> `tenants`, `subscriptions`, `payments`, `content_packs`, `teams`.
>
> Khi nào làm: sau khi MVP (m1–m12) xong và có nhu cầu thật.
> Bảng sẽ cần thêm (~9 bảng) đã liệt kê ở mục [Deferred trong 07-erd.md](./07-erd.md#deferred--không-có-trong-schema-mvp).
>
> **Đã reserve sẵn trong schema MVP** (tránh migration lớn về sau):
> - `users.role` có giá trị `PREMIUM` trong CHECK constraint (chưa dùng)
> - `users.auth_provider` có giá trị `GITHUB` (chưa dùng — MVP chỉ Google OAuth2); cột `oauth_id` NULL khi LOCAL
> - **Không** có cột `oauth_provider` (trùng `auth_provider`)

## Kiểu sản phẩm

| Model | Mô tả | Pricing |
|-------|-------|---------|
| **Cá nhân (self-host)** | Dùng cho bản thân, deploy nội bộ | Free (tự host) |
| **SaaS cho dev** | Multi-user, nhiều topic, cloud-host | Freemium / $9-15/tháng |
| **Team license** | Cho team phỏng vấn, onboarding | $49-99/tháng cho 10 người |
| **Content licensing** | Bán content packs (AWS, K8s, Spring Advanced) | $19-49/pack |
| **White-label** | Cho bootcamp / trung tâm đào tạo | Custom pricing |

## Freemium Model

```
┌─────────────────────────────────────────────────────┐
│  Free Tier                    │  Pro ($9.99/tháng)   │
│  ────────────────             │  ──────────────────  │
│  ✅ 1 topic (Java)           │  ✅ All topics       │
│  ✅ Flashcard mode           │  ✅ All modes        │
│  ✅ Basic quiz               │  ✅ Mock interview   │
│  ✅ Progress tracking        │  ✅ AI grading       │
│  ❌ Code challenge           │  ✅ Code challenge   │
│  ❌ Mindmap                  │  ✅ Mindmap          │
│  ❌ Export PDF                │  ✅ Export PDF       │
│  ❌ Priority support          │  ✅ Team features    │
└─────────────────────────────────────────────────────┘
```

## Content as Product

```
Marketplace:
├── 🆓 Java Core (free, 19 câu)
├── 🆓 Database & SQL (free, 43 câu)
├── 💰 AWS Solutions Architect ($29)
├── 💰 Kubernetes CKA ($29)
├── 💰 Spring Advanced ($19)
├── 💰 System Design ($39)
└── 📦 Bundle: Full Stack Prep ($99)
```

## Multi-tenant Architecture

> ⚠️ **Deferred.** ADR-002: MVP single-tenant, **không** có bảng `tenants`.
> Sơ đồ dưới là định hướng Phase 5+.

```
┌──────────────────────────────────────────────────┐
│  Tenant 1: "Dev Academy"                         │
│  ├── Students: 50                                │
│  ├── Custom topics: Java + Spring + AWS          │
│  ├── Branding: logo + colors                     │
│  └── Admin: instructor@academy.com               │
├──────────────────────────────────────────────────┤
│  Tenant 2: "My Personal Study"                   │
│  ├── Students: 1 (me)                            │
│  ├── Topics: all                                 │
│  └── Admin: me@email.com                         │
└──────────────────────────────────────────────────┘
```

## Revenue Streams

```
                    ┌──────────────────┐
                    │  Knowledge Gym   │
                    └────────┬─────────┘
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │  SaaS    │  │ Content  │  │ B2B      │
        │  Subs    │  │ Packs    │  │ License  │
        └────┬─────┘  └────┬─────┘  └────┬─────┘
             │              │              │
        Monthly/       One-time        Custom
        Annual         purchase        contracts
        $9-15/mo       $19-49          $500-5k/mo
```
