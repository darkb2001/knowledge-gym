#!/usr/bin/env bash
# Áp rule chống DDoS/lạm dụng lên Cloudflare cho zone darkb-tech.io.vn.
#
# Vì sao cần: Cloudflare đã hấp thụ L3/L4 (unmetered, mọi gói) và WAF free đã
# chặn UA xấu (sqlmap → 403), nhưng zone CHƯA có rate limiting rule nào — đo
# ngày 05/10: 1000 request dồn từ 1 IP vẫn vào tới nginx CT102 (chỉ ~5% bị
# shed). Rule ở đây chặn ngay tại edge, trước khi tốn băng thông tunnel + CPU
# CT102. Chi tiết mô hình phòng thủ: docs/29-ddos-protection.md.
#
# Cách chạy (trên CT102 hoặc máy nào cũng được, chỉ cần internet):
#   export CF_API_TOKEN='<token>'        # KHÔNG dán vào chat/không commit
#   ops/cloudflare/apply-ddos-rules.sh              # tạo/cập nhật rate limit
#   ops/cloudflare/apply-ddos-rules.sh --under-attack on    # bật kill-switch
#   ops/cloudflare/apply-ddos-rules.sh --under-attack off   # tắt sau khi qua cơn
#
# Token cần quyền: Zone → WAF → Edit (rate limiting rules), Zone → Zone
# Settings → Edit (security level / bot fight mode). Zone ID lấy tự động theo
# tên miền nếu không set CF_ZONE_ID.
#
# LƯU Ý: script PUT đè toàn bộ rules của phase http_ratelimit. Rule nào tạo tay
# trên dashboard mà không có trong danh sách dưới đây sẽ bị xoá.
set -euo pipefail

CF_API="${CF_API:-https://api.cloudflare.com/client/v4}"
ZONE_NAME="${CF_ZONE_NAME:-darkb-tech.io.vn}"
API_HOST="${KG_API_HOST:-api.darkb-tech.io.vn}"
TOKEN="${CF_API_TOKEN:-}"

die() { echo "LỖI: $*" >&2; exit 1; }
[ -n "$TOKEN" ] || die "thiếu CF_API_TOKEN"

api() { # api METHOD PATH [BODY]
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    curl -sS --fail-with-body -X "$method" "$CF_API$path" \
      -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
      --data "$body"
  else
    curl -sS --fail-with-body -X "$method" "$CF_API$path" \
      -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json'
  fi
}

zone_id() {
  if [ -n "${CF_ZONE_ID:-}" ]; then echo "$CF_ZONE_ID"; return; fi
  api GET "/zones?name=$ZONE_NAME" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["result"][0]["id"] if d.get("result") else "")'
}

apply_rate_limits() {
  local z="$1"
  local payload
  # GÓI FREE (kiểm chứng bằng API 05/10 — mọi mục dưới đây đều bị API trả 400 nếu sai):
  #   1. mỗi zone CHỈ 1 rate-limit rule;
  #   2. expression chỉ được dùng field `Path` (http.request.uri.path) — bản dùng `http.host`
  #      được API nhận nhưng KHÔNG bao giờ kích hoạt;
  #   3. characteristics bắt buộc có `cf.colo.id` (đếm theo colocation);
  #   4. period chỉ được = 10 (giây), mitigation_timeout chỉ được = 10 (giây).
  # Ngưỡng 250 req/10s/IP (~25 req/s): đủ rộng cho NAT dùng chung (CGNAT lớp học) nhưng vẫn
  # cắt được flood — hạ xuống nếu muốn chặt hơn.
  payload=$(cat <<JSON
{
  "rules": [
    {
      "description": "KG API: 250 req/10s/IP -> block 10s (flood API; Free: 1 rule/zone, period 10s, field Path)",
      "expression": "(starts_with(http.request.uri.path, \"/api/\"))",
      "action": "block",
      "ratelimit": {
        "characteristics": ["ip.src", "cf.colo.id"],
        "period": 10,
        "requests_per_period": 250,
        "mitigation_timeout": 10
      },
      "enabled": true
    }
  ]
}
JSON
)
  echo "→ PUT ruleset phase http_ratelimit (1 rule) cho zone $z"
  api PUT "/zones/$z/rulesets/phases/http_ratelimit/entrypoint" "$payload" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("result") or {}; print("   OK:", len(r.get("rules",[])), "rule(s), id:", r.get("id"))'
}

verify() { # verify [N] [PATH] — bắn N request song song và xác nhận Cloudflare chặn ở edge
  local n="${1:-60}" path="${2:-/api/v1/blog/posts}" dir
  dir="$(mktemp -d)"
  echo "→ bắn $n request song song tới https://$API_HOST$path"
  seq 1 "$n" | xargs -P 20 -I{} curl -s -o "$dir/{}" -w '%{http_code}\n' --max-time 20 "https://$API_HOST$path" | sort | uniq -c
  if grep -rqi 'error code: 1015' "$dir"; then
    echo "   ✓ Cloudflare đã chặn ở edge (error code: 1015) — rule hoạt động"
  else
    echo "   ! chưa thấy 1015 — hoặc ngưỡng chưa bị vượt (bình thường với vài chục request so với"
    echo "     ngưỡng 250 req/10s), hoặc expression sai. Muốn kiểm chứng cơ chế: bắn nhiều hơn"
    echo "     ngưỡng (vd: --verify 400) hoặc tạm hạ requests_per_period xuống 5."
  fi
  rm -rf "$dir"
}

setting() { # setting NAME VALUE
  local z="$1" name="$2" value="$3"
  echo "→ settings/$name = $value"
  api PATCH "/zones/$z/settings/$name" "{\"value\":\"$value\"}" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("result") or {}; print("   OK:", r.get("id"), "=", r.get("value"))' \
    || echo "   (bỏ qua: token thiếu quyền hoặc setting này không dùng API được — làm trên dashboard)"
}

main() {
  local z; z="$(zone_id)"
  [ -n "$z" ] || die "không tìm thấy zone $ZONE_NAME (token thiếu quyền Zone:Read?)"
  echo "zone $ZONE_NAME = $z"
  case "${1:-}" in
    --under-attack)
      case "${2:-}" in
        on)  setting "$z" security_level under_attack ;;
        off) setting "$z" security_level medium ;;
        *)   die "dùng: --under-attack on|off" ;;
      esac
      ;;
    --security-level)
      setting "$z" security_level "${2:?high|medium|low}" ;;
    --verify)
      verify "${2:-60}" "${3:-/api/v1/blog/posts}" ;;
    *)
      apply_rate_limits "$z"
      # Bot Fight Mode: hữu ích nhưng API có thể khác theo gói → lỗi không chặn.
      setting "$z" bot_fight_mode on || true
      echo
      echo "Còn 2 việc nên làm bằng dashboard (không API được chắc chắn):"
      echo "  1. Security → WAF → Managed rules: bật Cloudflare Free Managed Ruleset."
      echo "  2. Security → Bots: bật Bot Fight Mode nếu bước trên báo bỏ qua."
      ;;
  esac
}

main "$@"
