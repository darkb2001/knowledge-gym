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
  payload=$(cat <<JSON
{
  "rules": [
    {
      "description": "KG auth: 60 req/phút/IP → managed challenge (brute force login/OTP)",
      "expression": "(http.host eq \"$API_HOST\" and starts_with(http.request.uri.path, \"/api/v1/auth/\"))",
      "action": "managed_challenge",
      "ratelimit": {
        "characteristics": ["ip.src"],
        "period": 60,
        "requests_per_period": 60,
        "mitigation_timeout": 300
      }
    },
    {
      "description": "KG api: 200 req/10s/IP → block 60s (flood API)",
      "expression": "(http.host eq \"$API_HOST\" and starts_with(http.request.uri.path, \"/api/\"))",
      "action": "block",
      "ratelimit": {
        "characteristics": ["ip.src"],
        "period": 10,
        "requests_per_period": 200,
        "mitigation_timeout": 60
      }
    }
  ]
}
JSON
)
  echo "→ PUT ruleset phase http_ratelimit (2 rule) cho zone $z"
  api PUT "/zones/$z/rulesets/phases/http_ratelimit/entrypoint" "$payload" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("result") or {}; print("   OK:", len(r.get("rules",[])), "rule(s), id:", r.get("id"))'
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
