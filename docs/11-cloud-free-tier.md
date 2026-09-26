# Infrastructure — Proxmox VE (server local) + Cloud Free-Tier

> **Nguyên tắc:** Server local = **Proxmox VE 9.2.5** (node `darkb`, 12 cores) → **tất cả self-host trên đó**.
> Cloud chỉ giữ **AWS Lambda + EventBridge** (cron — free *vĩnh viễn* theo quota, không phải 12 tháng) + **offsite backup B2**.
> **Email: Mailu** — self-host mail server trên 1 LXC riêng trên PVE (**bỏ AWS SES** — free tier SES đã hết 12 tháng → bắt đầu tính tiền từ 06/2026).
> **Object storage**: **Garage** (S3-compatible, self-host, free forever — MinIO CE đã archived 02/2026).
> **Backup 2 lớp**: **PBS** (snapshot LXC/VM — restore bootable) + **restic → Backblaze B2** (pg_dump app-consistent, offsite free).
> **IaC: Terraform** — AWS serverless + provisioning LXC trên PVE (provider `bpg/proxmox`).

## Phân bổ: Cái gì ở đâu

| Thành phần | Chạy ở đâu | Lý do |
|---|---|---|
| Backend (Spring Boot) + 9 services | **LXC `102` (knowledge-gym)** trên PVE → Docker Compose | Unprivileged + `nesting=1` cho Docker |
| PostgreSQL 16, Redis, Kafka, ES, Garage, Nginx, Prometheus, Grafana | **Cùng LXC 102** (Docker) | Đã có server, low latency |
| **Email production** | **LXC `103` (mailu)** — Mailu self-host | Free vĩnh viễn, không phụ thuộc AWS SES |
| **Cron jobs** (collector 6h, daily challenge, backup trigger) | **AWS Lambda + EventBridge** | Free vĩnh viễn theo quota (1M req/tháng) |
| **Frontend Next.js** | **Firebase Hosting** hoặc **GitHub Pages** | Free CDN, static export — không dính AWS bill |
| **LXC/VM backup** (lớp 1) | **PBS** (Proxmox Backup Server) trên PVE | Snapshot bootable, dedup block-level, free |
| **DB backup offsite** (lớp 2) | **restic → Backblaze B2** | 10 GB free vĩnh viễn, encrypted, app-consistent |

## Phân bổ tài nguyên trên PVE (node `darkb`)

LXC hiện có: `100 (ubuntu)`, `101 (hermes-agent)` → các ID mới bắt đầu từ `102`.

| LXC | ID | vCPU | RAM | Disk | Nội dung |
|---|---|---|---|---|---|
| knowledge-gym | **102** | 4 | 12 GB | 100 GB (SSD) | Docker Compose 9 services |
| mailu | **103** | 1 | 1–1.5 GB | 20 GB | Mailu (postfix+dovecot+rspamd+admin) |
| pbs (tùy chọn) | **104** | 2 | 2 GB | datastore → ổ riêng | Proxmox Backup Server |

**Tạo LXC 102 (unprivileged, cho Docker):**

```bash
# Trên PVE host — template Ubuntu 24.04
pct create 102 local:vztmpl/ubuntu-24.04-standard_24.04-2_amd64.tar.zst \
  --storage local-lvm --rootfs local-lvm:100 \
  --cores 4 --memory 12288 --net0 name=eth0,bridge=vmbr0,ip=dhcp \
  --unprivileged 1 --features nesting=1 --hostname knowledge-gym

pct start 102 && pct exec 102 -- bash
# Inside LXC: cài Docker + git clone repo + docker compose up -d
```

> `features nesting=1` là **bắt buộc** để Docker chạy trong LXC unprivileged.
> Nếu ES gặp lỗi `max virtual memory areas vm.max_map_count` → thêm trên PVE host:
> `echo "vm.max_map_count=262144" >> /etc/sysctl.conf && sysctl -p`

**Lưu ý:** 12 GB RAM là mức cho đủ 9 services (ES heap 2 GB, Kafka 1 GB, PG 1 GB, app 2 GB, còn lại infra). Nếu RAM máy tổng < 16 GB → cân nhắc tách ES/Kafka sang LXC khác hoặc giảm ES heap.

## AWS Cloud — chỉ 2 thứ serverless (free vĩnh viễn)

```
┌─ AWS Free Tier — ⚠️ phân biệt 2 loại free ─────────────────────┐
│                                                                  │
│  ❌ 12-THÁNG (đã hết hạn → tính tiền):                          │
│     EC2 t2.micro, S3 5GB, RDS, SES 3,000 email/tháng            │
│     → KHÔNG dùng bất kỳ cái nào trong nhóm này                  │
│                                                                  │
│  ✅ FREE VĨNH VIỄN (không giới hạn thời gian):                   │
│     1. AWS Lambda      → 1M req/tháng + 400K GB-s               │
│     2. AWS EventBridge → ~14M events/tháng (cron rules)          │
│     → Dùng cho 3 cron job: collector 6h, daily-challenge,        │
│       backup trigger 3h (30 lần/ngày × 3 = ~2,700 req/tháng)     │
│                                                                  │
│  ⚠️ Vẫn kiểm tra Billing console + đặt Billing Alarm            │
│     (Budgets free) để không bị surprise.                         │
└──────────────────────────────────────────────────────────────────┘

┌─ Cloud (Backblaze B2 — Free vĩnh viễn, no credit card) ─────────┐
│                                                                  │
│  restic → b2:kg-db-backups                                      │
│  • 10 GB storage free (vĩnh viễn, không cần thẻ)                │
│  • Encrypted + incremental + deduplicated                        │
│  • Retention: 7 daily, 4 weekly, 6 monthly                       │
│  • Egress free: 3× stored data/month                            │
│                                                                  │
│  Signup: backblaze.com/b2 → Tạo bucket "kg-db-backups"          │
│  Key: Tạo Application Key (read+write+list bucket)              │
│  Set env: B2_KEY_ID + B2_APP_KEY + RESTIC_PASSWORD_FILE         │
└──────────────────────────────────────────────────────────────────┘
```

```
┌─ PVE node `darkb` (Proxmox VE 9.2.5) ──────────────────────────┐
│                                                                  │
│  LXC 100 (ubuntu) ─── đã có                                      │
│  LXC 101 (hermes-agent) ─── đã có                                │
│  LXC 102 (knowledge-gym) ─── MỚI: Docker Compose                 │
│     nginx ─┬─ app (Spring Boot) ─┬─ postgres                      │
│            │                     ├─ redis (auth + cache + locks)  │
│            │                     ├─ kafka                         │
│            │                     ├─ elasticsearch                 │
│            │                     └─ garage (S3, port 3900)        │
│            ├─ prometheus ── grafana                               │
│            └─ frontend (Next.js static build)                     │
│  LXC 103 (mailu) ─── MỚI: self-host email                        │
│  LXC 104 (pbs) ─── MỚI (tùy chọn): Proxmox Backup Server        │
│                                                                  │
│  Cron → AWS Lambda + EventBridge (free vĩnh viễn)                │
│  FE   → Firebase Hosting / GitHub Pages (free)                   │
│  Offsite → restic → Backblaze B2 (10 GB free)                    │
└──────────────────────────────────────────────────────────────────┘
```

## Email — Mailu self-host (thay AWS SES)

**Tại sao bỏ SES:** SES 3,000 email/tháng free chỉ trong **12 tháng đầu** tài khoản AWS — sau đó tính $0.10/1,000 email + vẫn cần maintain AWS account. **Mailu** = self-host, free vĩnh viễn, full control.

**Mailu là gì:** full mail suite open-source (Postfix + Dovecot + rspamd + DKIM + admin UI), chạy bằng Docker Compose trên LXC 103.

**Setup:**

```bash
pct create 103 local:vztmpl/ubuntu-24.04-standard_*.tar.zst \
  --storage local-lvm --rootfs local-lvm:20 --cores 1 --memory 1536 \
  --unprivileged 1 --features nesting=1 --hostname mailu
pct start 103 && pct exec 103 -- bash

git clone https://github.com/Mailu/Mailu.git /opt/mailu
cd /opt/mailu && cp docker-compose.yml.dist docker-compose.yml
# Admin UI: https://mail.darkb-tech.io.vn (port 443 qua nginx)
```

**DNS cần trỏ (DNS provider của `darkb-tech.io.vn`):**

| Record | Type | Value |
|---|---|---|
| `mail` | A | IP public server |
| MX | `mail.darkb-tech.io.vn` (priority 10) | — |
| SPF | TXT | `v=spf1 mx a:mail.darkb-tech.io.vn -all` |
| DMARC | TXT `_dmarc` | `v=DMARC1; p=quarantine; rua=mailto:dmarc@darkb-tech.io.vn` |
| DKIM | Mailu admin generate → TXT | — |

**App kết nối Mailu (Spring `application-prod.yml`):**

```yaml
spring:
  mail:
    host: 192.168.x.x        # LXC 103 IP (internal) hoặc mail.darkb-tech.io.vn
    port: 587
    username: no-reply@darkb-tech.io.vn
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

```java
@Service @Profile("prod")
public class MailuEmailAdapter implements EmailPort {
    private final JavaMailSender mailSender;   // spring-boot-starter-mail

    public void sendPasswordResetCode(String to, String code) {
        MimeMessage m = mailSender.createMimeMessage();
        new MimeMessageHelper(m, "UTF-8").setTo(to)
            .setSubject("Knowledge Gym — Mã đặt lại mật khẩu")
            .setText("Mã: " + code + " (hết hạn 10 phút)", true);
        mailSender.send(m);
    }
}
```

**⚠️ Deliverability (quan trọng khi self-host mail):**
- **Cần IP public tĩnh + rDNS (PTR) trỏ về `mail.darkb-tech.io.vn`** — check: `dig -x <IP>`
- Gmail/Yahoo要求 SPF + DKIM + DMARC hợp lệ (bắt buộc từ 02/2024) → cấu hình đủ 3 record
- Warm-up: vài tuần đầu gửi ít (OTP/notifications ~ vài chục/ngày → không vấn đề)
- Test: mail-test.com / GlockApps kiểm tra spam score trước khi launch
- Nếu IP bị block (dynamic IP của ISP) → fallback: **Relay sang provider free** (Mailu hỗ trợ `RELAYHOST`): Brevo free 300/ngày hoặc Resend SMTP free 3,000/tháng — Mailu vẫn là SMTP origin, deliverability do provider lo

## Garage — Object Storage (thay MinIO CE)

**Tại sao Garage thay MinIO:**
- MinIO Community Edition **archived 02/2026**, repository read-only, không còn update
- MinIO AIStor Free: proprietary single-node, cần license key → không còn open source
- **Garage** (by Deuxfleurs): AGPLv3, Rust, v2.3.0 (April 2026), actively maintained, S3-compatible

| Feature | Garage v2.3.0 | MinIO CE (archived) |
|---|---|---|
| S3 API | ✅ S3-compatible | ✅ S3-compatible |
| License | AGPLv3 | AGPLv3 (archived) |
| Maintenance | ✅ Actively maintained | ❌ Archived, no update |
| Multi-node | ✅ Designed for it | ✅ |
| Language | Rust | Go |
| Container image | `deuxfleurs/garage:v2.3.0` | ❌ No official image |

```yaml
  garage:
    image: deuxfleurs/garage:v2.3.0
    command: garage server -c /etc/garage.toml
    volumes:
      - garage-data:/garage
      - ./infra/garage.toml:/etc/garage.toml:ro
    ports:
      - "3900:3900"   # S3 API
      - "3901:3901"   # Admin
```

**Use cases với Garage:**

| Use case | Bucket | Ghi chú |
|---|---|---|
| Blog cover images | `kg-blog-images` | Upload qua presigned URL |
| Avatar users | `kg-avatars` | Resize trước upload |
| Export (notes, quiz PDF) | `kg-exports` | TTL lifecycle 7 ngày |

**Setup:** `bash scripts/setup-garage.sh` (tạo 3 buckets + access key)

## Backup 2 lớp — PBS + restic → B2

Server chạy **Proxmox VE** → dùng **cả 2 lớp** (không phải lựa chọn 1 trong 2):

| Lớp | Tool | Unit of restore | Protects against |
|---|---|---|---|
| **Lớp 1: Infra** | **PBS** (snapshot LXC/VM) | Cả máy (bootable) | Host hỏng, rollback sau update, mất LXC |
| **Lớp 2: App/Data** | **pg_dump + restic → B2** | File / SQL dump | Ransomware, xóa nhầm DB, mất cả server + ổ |

**Tại sao cần cả 2:**
- PBS local **không cứu được** nếu ổ cứng host hỏng (datastore cùng ổ) → cần offsite
- restic/B2 **không boot được** máy → khôi phục infra chậm hơn
- pg_dump là **app-consistent** — PBS snapshot chỉ crash-consistent (DB có thể cần recovery)

### Lớp 1 — PBS (Proxmox Backup Server)

- **Free**: AGPLv3, community repo free (Enterprise repo €560/năm chỉ có support — KHÔNG cần)
- Cài qua `proxmox-backup-server` ISO (VM) hoặc trên Debian (LXC 104)
- PVE → Datacenter → Backup: chọn datastore PBS thay vì local storage
- Dedup block-level, client-side encryption, verify jobs

```bash
# backup LXC 102 hằng đêm (PVE GUI: Datacenter → Backup → Add)
# vzdump 102 --storage pbs --mode snapshot --compress zstd
# → snapshot crash-consistent → NGAY TRƯỚC backup nên hook pg_dump (xem dưới)
```

**⚠️ Best practice homelab (1 máy):** PBS chạy cùng máy với PVE → cả 2 chết chung khi ổ hỏng.
Chấp nhận được vì **lớp 2 (B2) lo offsite**. Nếu có máy thứ 2 → PBS sync giữa 2 datastore (chỉ transfer delta).

### Lớp 2 — pg_dump + restic → Backblaze B2 (offsite)

**Stack:**
- **pg_dump** — PostgreSQL native backup (app-consistent)
- **restic** — encrypted, incremental, deduplicated backup
- **Backblaze B2** — 10 GB free storage forever (no credit card, no 12-month limit)

```bash
# Setup (1 lần)
brew install restic   # hoặc apt install restic trên LXC
b2 create-bucket kg-db-backups allPrivate
b2 authorize-account <B2_KEY_ID> <B2_APP_KEY>
restic -r b2:kg-db-backups init

# Daily backup (cron LXC 102: 0 3 * * *)
bash scripts/backup-db.sh

# Restore test (QUARTERLY — bắt buộc)
restic -r b2:kg-db-backups snapshots --tag kg-db
restic -r b2:kg-db-backups restore latest --target /tmp/kg-restore
pg_restore -h localhost -U postgres -d knowledgegym_new /tmp/kg-restore/data/kg-*.sql.gz
```

**Free tier B2:**

| Item | Free tier |
|---|---|
| Storage | **10 GB** (permanent, no time limit) |
| Egress | 3× stored data/month free |
| Class A API (writes) | Unlimited |
| Class B API (reads) | 2,500/day |

**Nếu DB > 10 GB:** giảm retention (`keep-monthly: 3`) hoặc upgrade ($6/TB — rất rẻ)

**Trigger backup:** AWS Lambda + EventBridge (cron 3h) → gọi `POST /internal/backup` trên app → app chạy `backup-db.sh` (hoặc Lambda SSH/curl thẳng endpoint). Xem [ADR-004](#adr-004).

### PBS vs restic — vì sao dùng cả 2

| Tiêu chí | PBS | restic → B2 |
|---|---|---|
| **Backup unit** | LXC/VM (bootable) | File + pg_dump (logical) |
| **App-consistent DB** | ❌ Crash-consistent | ✅ pg_dump luôn consistent |
| **Dedup** | ✅ Block-level (rất mạnh) | ✅ File chunk-level |
| **Encryption** | ✅ Client-side AES-256 | ✅ AES-256 |
| **Offsite** | Sync sang PBS thứ 2 (cần máy 2) | ✅ Native → B2 free vĩnh viễn |
| **Restore** | Boot LXC/VM (<5 phút) | `restic restore` + `pg_restore` |
| **Cost** | $0 (community repo) | $0 (B2 10 GB) |

**Restore drills (bắt buộc):**
- **Quarterly:** `restic restore` + `pg_restore` vào DB test — verify SQL hợp lệ
- **Quarterly:** boot thử snapshot PBS của LXC 102 — verify "restore ends in a login prompt"

## Cron jobs — AWS Lambda + EventBridge (free vĩnh viễn)

**Vì sao giữ:** Lambda 1M req/tháng + EventBridge ~14M events/tháng = **free vĩnh viễn**
(khác SES — 12 tháng rồi tính tiền). Quy mô project: 3 cron × 8 lần/ngày ≈ **720 req/tháng** → xa ngưỡng free.

| Job | Schedule | Gọi cái gì |
|---|---|---|
| Blog collector | `rate(6 hours)` | `POST /internal/collect` (token) |
| Daily challenge | `rate(6 hours)` | `POST /internal/challenge/daily` (token) |
| DB backup trigger | `rate(3 hours)` | `POST /internal/backup` (token) |

```hcl
resource "aws_lambda_function" "collector" {
  filename      = "collector.zip"
  function_name = "kg-collector"
  role          = aws_iam_role.lambda.arn
  handler       = "index.handler"
  runtime       = "nodejs20.x"
  timeout       = 30
  environment {
    variables = {
      BACKEND_URL = "https://server.darkb-tech.io.vn"
      CRON_TOKEN  = var.cron_secret
    }
  }
}

resource "aws_cloudwatch_event_rule" "collector" {
  name                = "kg-collector-6h"
  schedule_expression = "rate(6 hours)"
}
```

**⚠️ Vẫn làm:** AWS Billing console → Budgets → tạo Budget alarm ở $1 (free) để phòng ngừa.

## Terraform (IaC)

```
infra/terraform/
├── main.tf              # providers: aws + bpg/proxmox
├── variables.tf         # region, email, cron_secret, pmox_url
├── outputs.tf           # lambda_arn, lxc_vmid
├── lambda/
│   ├── main.tf          # 3 functions + IAM role + EventBridge rules
│   ├── collector/index.mjs
│   ├── daily/index.mjs
│   └── backup/index.mjs
├── proxmox/
│   └── lxc.tf           # LXC 102 (knowledge-gym) + LXC 103 (mailu)
└── backend.tf           # local state (hoặc backend Garage)
```

```hcl
# Proxmox provider — IaC cho LXC trên PVE
terraform {
  required_providers {
    proxmox = {
      source  = "bpg/proxmox"
      version = "~> 0.66"
    }
  }
}

provider "proxmox" {
  endpoint = "https://pve.darkb-tech.io.vn:8006/api2/json"
  username = var.pmox_user        # api-token@pve!kg=xxx
  insecure = true
}
```

## FE Hosting — 2 lựa chọn free

### Option A: Firebase Hosting (khuyến nghị)
```bash
cd kg-frontend && npm run build
firebase deploy --only hosting   # free 10 GB CDN
```
Free: 10 GB storage + 10 GB egress/tháng. CDN global, SSL auto.

### Option B: GitHub Pages
```yaml
# .github/workflows/deploy-fe.yml
- uses: peaceiris/actions-gh-pages@v3
  with: { publish_dir: ./kg-frontend/out }
```
Free: unlimited static hosting.

### Option C: Serve từ LXC 102 (đơn giản nhất)
```nginx
server {
    listen 80;
    root /var/www/kg-frontend;
    location / { try_files $uri /index.html; }
    location /api/ { proxy_pass http://app:8080; }
}
```
Free. Cần SSL (Let's Encrypt). Không CDN.

## Cost Summary

```
PVE server (đã có)               $0     (self-host everything)
LXC 102 + Docker Compose         $0
Mailu (email self-host)          $0     (free vĩnh viễn — cần domain + IP sạch)
PBS (snapshot LXC/VM)            $0     (community repo)
Garage (object storage)          $0     (self-host Docker)
restic → Backblaze B2            $0     (10 GB free vĩnh viễn)
AWS Lambda + EventBridge         $0     (free VĨNH VIỄN theo quota — không phải 12 tháng)
Firebase Hosting (FE)            $0     (10 GB free)
─────────────────────────────────────────────────────────────
TOTAL                            $0     (hoàn toàn free)
```

**Kiểm tra bill AWS:** AWS account free tier hết hạn → vào **Billing console** xác nhận chỉ còn Lambda/EventBridge (free vĩnh viễn). Đặt **Budgets alarm $1** (miễn phí) phòng ngừa.

## ADR-004: Infra + Backup + Email Strategy

- **Decision:** Toàn bộ app self-host trên PVE (LXC + Docker Compose). Email = Mailu self-host (bỏ AWS SES — free tier 12 tháng đã hết). Cron = AWS Lambda + EventBridge (free vĩnh viễn theo quota, giữ lại). Backup 2 lớp = PBS (snapshot) + restic → B2 (offsite app-consistent). Object storage = Garage.
- **Context:** Server Proxmox VE 9.2.5 riêng (`darkb`, 12 cores) đã chạy 2 LXC. AWS free tier 12 tháng đã hết → tránh mọi dịch vụ có thời hạn free (SES, EC2, S3). MinIO CE archived 02/2026 → Garage. S3 free 12 tháng → B2 free vĩnh viễn.
- **Consequences:**
  - $0 cost hoàn toàn — không có dịch vụ nào có free tier 12 tháng
  - Mailu cần IP tĩnh + rDNS + SPF/DKIM/DMARC → deliverability phải test (mail-test.com); fallback RELAYHOST sang Brevo/Resend free
  - PBS cùng ổ với PVE → không chống ổ hỏng, nhờ B2 lo offsite
  - PBS snapshot crash-consistent → luôn pg_dump trước, restic là nguồn restore DB chính
  - FE Firebase static export — không SSR
  - AWS Billing Alarm $1 làm safety net
