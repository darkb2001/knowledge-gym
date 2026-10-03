# Handoff — Proxmox ops bot (đóng m11 trên CT100/101/102)

> Dán toàn bộ file này cho bot có quyền quản trị PVE + LXC.
> Repo code m11a/m11b đã ship trên `main` (`knowledge-gym`). Việc còn lại = **verify + cron + secrets trên máy thật**.

## Vai trò CT (đừng đụng nhầm)

| CT | Vai trò | Quy tắc |
|---|---|---|
| **CT100** | NPM / edge dùng chung (LLM gateway…) | **Không** đưa API Knowledge Gym qua NPM trừ khi tunnel CT102 chết. Không restart ẩu. |
| **CT101** | (infra khác trên node) | Không lấy RAM của CT102. Chỉ đo/report nếu cần capacity. |
| **CT102** | Knowledge Gym prod (`docker-compose.prod.yml`, 9 services + cloudflared) | **Primary work target.** Docker + compose + cron + **kg-es-control** chạy **trong CT102**, không phải trên node PVE. |

Mailu / PBS / Firebase: **không tạo**, đã deferred (ADR-004).

> “Host helper” trong docs cũ = **host của Docker = CT102**, không phải Proxmox hypervisor.
> App container **không** gọi được `127.0.0.1` trên CT102 — dùng HTTP agent bind bridge gateway
> (xem `docs/22-es-control-ops-bot-reply.md`).

---

## Credential — bot KHÔNG tự bịa

Operator (người) phải **đã có hoặc sẽ paste** vào CT102 / GitHub. Bot chỉ **cài đặt + kiểm tra**, không tạo tài khoản cloud hộ trừ khi operator đưa key.

| Secret | Ai tạo | Bot làm gì |
|---|---|---|
| `.env` prod (JWT, Postgres, Garage, Google, CRON, mail…) | Operator | Verify file tồn tại `chmod 600`, không commit; thiếu biến `:?` thì báo |
| B2 `B2_KEY_ID` + `B2_APPLICATION_KEY` | Operator (Backblaze) | Cài vào env host / file an toàn; chạy backup |
| `/etc/kg-backup/restic.pw` | Operator (`openssl rand -base64 32`) | Tạo file nếu được phép + `chmod 600`; init restic repo nếu chưa |
| Grafana Cloud remote_write user/token | Operator | Ghi `infra/prometheus/remote_write.secrets.yml`, merge scrape |
| GitHub `DEPLOY_*` + `DEPLOY_ENABLED` | Operator | Optional: tạo SSH user/key trên CT102 cho Actions, báo public key |
| AWS Terraform | Operator | **Skip** trừ khi operator bảo apply; ưu tiên host cron |

---

## Việc bot PHẢI làm (theo thứ tự)

### 0. Inventory (đọc trước, đừng sửa)

Trên PVE host:

```bash
pvesh get /nodes/$(hostname -s)/status
pct config 100; pct config 101; pct config 102
pct list
df -h; free -h
cat /etc/sysctl.d/99-kg.conf 2>/dev/null || echo "MISSING 99-kg.conf"
```

Báo: RAM allocated vs used từng CT, thin pool `%`, CT102 có `nesting=1,keyctl=1`, unprivileged, disk, onboot.

### 1. Host sysctl (nếu thiếu)

File `/etc/sysctl.d/99-kg.conf`:

```
vm.max_map_count=262144
fs.file-max=2097152
vm.swappiness=10
net.core.somaxconn=4096
```

`sysctl --system`. Không cần restart CT theo plan.

### 2. CT102 Docker stack health

```bash
pct enter 102   # hoặc ssh vào CT102
cd /opt/kg   # hoặc DEPLOY_ROOT thật
docker compose -f docker-compose.prod.yml ps
curl -si http://127.0.0.1/api/v1/actuator/health | head -40
curl -si http://127.0.0.1/ | head -20   # expect 404 by design
docker compose -f docker-compose.prod.yml exec -T elasticsearch curl -s localhost:9200/_cluster/health
```

**Pass:** 9/9 `Up` (healthy chỉ nơi có healthcheck); actuator healthy; ES ≥ yellow; `/` = 404 OK.

Pull image mới nếu cần (GHCR `ghcr.io/darkb2001/knowledge-gym-app:latest`) — **`compose pull && up -d`, tuyệt đối không `up --build` trên LXC 8GB**.

### 3. Cloudflare Tunnel

- cloudflared trên **host CT102** → `http://127.0.0.1:80`
- Public: `https://api.darkb-tech.io.vn/api/v1/actuator/health`
- Không expose db/redis/kafka/es/garage/grafana ra LAN/WAN

### 4. Garage + upload path

- Buckets tồn tại (`scripts/setup-garage.sh` nếu thiếu): ít nhất `kg-avatars`
- `.env` có `S3_PUBLIC_ENDPOINT` = hostname **browser** resolve được (tunnel), **không** phải `http://garage:3900`
- Smoke: từ ngoài, presigned PUT avatar (hoặc `curl` PUT URL từ API) thành công

### 5. Host cron (bắt buộc cho m11b durability)

Trên **CT102 host** (nơi có `docker`, `pg_dump`, `restic`):

```cron
0 3 * * *  GARAGE_BACKUP=1 CONFIG_BACKUP=1 /opt/kg/scripts/backup-db.sh >> /var/log/kg-backup.log 2>&1
15 4 * * * ES_URL=http://elasticsearch:9200 /opt/kg/scripts/es-retention.sh >> /var/log/kg-es-retention.log 2>&1
```

Cài `restic`, `postgresql-client` nếu thiếu. **Không** mount `docker.sock` vào container `app`.

### 6. Restore drill (1 lần, bắt buộc)

Với B2 + restic đã cấu hình:

```bash
# tạo DB scratch knowledgegym_restore
SKIP_PG_RESTORE=0 /opt/kg/scripts/restore-drill.sh
```

Ghi kết quả vào bảng log trong `docs/20-backup-restore-drill.md` (date, snapshot id, pass/fail).  
**Không** ghi đè volume Garage prod trong lần drill đầu.

### 7. ES admin lifecycle

- `scripts/es-control.sh status|start|stop` chạy được **trên host CT102**
- App gọi script qua `KG_ES_CONTROL_SCRIPT` — nếu app trong container không exec được host script: cài **host agent mỏng** hoặc document rằng admin UI lifecycle chỉ chạy khi gọi từ host; **cấm** gắn docker.sock vào app

### 8. Observability

- Prometheus scrape app + nginx UP
- Grafana provisioning mount có dashboard JVM/HTTP + nginx
- (Optional) remote_write Grafana Cloud nếu operator đưa token

### 9. CI deploy hook (optional)

- User SSH deploy trên CT102 (non-root, key-only, sudo hạn chế compose)
- Báo public key để operator dán GitHub secrets + `DEPLOY_ENABLED=true`

---

## Cấm

1. `docker compose ... up --build` trên CT102  
2. Mount docker.sock vào Spring `app`  
3. Publish port ES/Kafka/DB/Garage/Grafana ra `0.0.0.0`  
4. Đưa API KG qua NPM CT100 trừ disaster fallback  
5. Tạo Mailu / PBS / Firebase  
6. Commit `.env`, restic password, B2 keys, terraform.tfvars  
7. Force-push / xoá volume prod khi drill  

---

## Báo cáo trả về (bắt buộc format)

```text
## PVE inventory
- node RAM used/total:
- CT100/101/102 alloc + used:
- thin pool %:
- sysctl 99-kg.conf: OK|MISSING|APPLIED

## CT102 stack
- compose ps: (9 lines)
- actuator health:
- ES health:
- tunnel public health:

## Garage / S3
- buckets:
- S3_PUBLIC_ENDPOINT:
- upload smoke: PASS|FAIL|SKIP(+reason)

## Backup
- restic snapshots (last 3):
- restore drill: PASS|FAIL|SKIP(+reason)
- cron installed: yes/no

## ES retention cron: yes/no
## ES control script: OK|FAIL
## Grafana scrape: OK|FAIL
## Deploy SSH user: created|skipped

## Blockers (credentials còn thiếu)
- ...
```

Làm xong checklist trên = m11 **ops-closed**. Phần code không cần bot Proxmox sửa trừ khi smoke phát hiện bug — khi đó mở issue với log, không “sửa tạm” trên prod.
