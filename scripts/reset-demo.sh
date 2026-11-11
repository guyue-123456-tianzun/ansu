#!/usr/bin/env bash
set -euo pipefail

# 需要 /api/demo/reset（下一批实现 DemoService 后可用）
BASE="${BASE:-http://127.0.0.1:8080}"
TOKEN="${TOKEN:-}"

if [[ -z "$TOKEN" ]]; then
  TOKEN="$(curl -fsS -X POST "$BASE/api/auth/demo" \
    -H 'Content-Type: application/json' \
    -d '{"role":"admin"}' | jq -r '.data.token')"
fi

curl -fsS -X POST "$BASE/api/demo/reset" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"confirm":"RESET-DEMO"}' | jq .
