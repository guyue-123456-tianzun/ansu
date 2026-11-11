#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:8080}"

echo "== health =="
curl -fsS "$BASE/api/health" | jq .
echo

echo "== demo login (student 314) =="
TOKEN="$(curl -fsS -X POST "$BASE/api/auth/demo" \
  -H 'Content-Type: application/json' \
  -d '{"role":"student","dormId":314}' | jq -r '.data.token')"
echo "token: ${TOKEN:0:24}..."
echo

echo "== thresholds =="
curl -fsS "$BASE/api/safety/thresholds" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.version'
echo

echo "== unauthorized check =="
curl -s -o /dev/null -w "%{http_code}\n" "$BASE/api/safety/thresholds"
