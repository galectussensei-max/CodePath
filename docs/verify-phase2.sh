#!/usr/bin/env bash
# Phase 2 verification: hidden test cases and diagnostic answer keys must never leak.
# Run from Git Bash with Postgres + the app running:  bash docs/verify-phase2.sh
set -u
API="${API_URL:-http://localhost:8080}"
PG="${PG_CONTAINER:-codepath-postgres}"
J='Content-Type: application/json'
PASS=0; FAIL=0
check() { if [ "$2" = "$3" ]; then echo "  PASS  $1"; PASS=$((PASS+1)); else echo "  FAIL  $1 (expected: $2, actual: $3)"; FAIL=$((FAIL+1)); fi; }
sql() { docker exec "$PG" psql -U codepath -d codepath -At -c "$1" 2>/dev/null | tr -d '[:space:]'; }
get() { curl -s -H "Authorization: Bearer $TOKEN" "$API$1"; }
code() { curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$API$1"; }

EMAIL="p2.$(date +%s)@example.com"
TOKEN=$(curl -s -X POST "$API/api/auth/register" -H "$J" -d "{\"name\":\"P2\",\"email\":\"$EMAIL\",\"password\":\"Passw0rd-verify\"}" \
  | grep -o '"accessToken":"[^"]*"' | head -1 | sed 's/"accessToken":"//; s/"$//')

echo "== 2.3 problem bank =="
LIST=$(get /api/questions)
check "list returns 15 problems"                 15 "$(echo "$LIST" | grep -o '"slug"' | wc -l | tr -d ' ')"
check "list excludes diagnostic questions"        0 "$(echo "$LIST" | grep -c 'diag-')"
check "list has no test case data"                0 "$(echo "$LIST" | grep -ciE 'expectedOutput|hidden|sampleTestCases')"
check "filter topicId=1 returns 3"                3 "$(get '/api/questions?topicId=1' | grep -o '"slug"' | wc -l | tr -d ' ')"
EASY_DB=$(sql "select count(*) from questions where difficulty='EASY' and placement_tags <> 'DIAGNOSTIC'")
check "filter difficulty=EASY matches DB"  "$EASY_DB" "$(get '/api/questions?difficulty=EASY' | grep -o '"slug"' | wc -l | tr -d ' ')"
check "bad difficulty returns 400"              400 "$(code '/api/questions?difficulty=EXTREME')"

SLUG=$(echo "$LIST" | grep -o '"slug":"[^"]*"' | head -1 | sed 's/"slug":"//; s/"$//')
DETAIL=$(get "/api/questions/$SLUG")
PUBLIC_DB=$(sql "select count(*) from test_cases t join questions q on q.id=t.question_id where q.slug='$SLUG' and not t.is_hidden")
HIDDEN_DB=$(sql "select count(*) from test_cases t join questions q on q.id=t.question_id where q.slug='$SLUG' and t.is_hidden")
check "detail returns only public cases ($SLUG)" "$PUBLIC_DB" "$(echo "$DETAIL" | grep -o '"expectedOutput"' | wc -l | tr -d ' ')"
check "problem has hidden cases in DB (>0)"       1 "$([ "${HIDDEN_DB:-0}" -gt 0 ] && echo 1 || echo 0)"
check "detail JSON has no hidden flag"            0 "$(echo "$DETAIL" | grep -ciE 'hidden|is_hidden|isHidden')"

echo "== every problem: public-case count in API == public count in DB =="
BAD=0
for s in $(echo "$LIST" | grep -o '"slug":"[^"]*"' | sed 's/"slug":"//; s/"$//'); do
  api=$(get "/api/questions/$s" | grep -o '"expectedOutput"' | wc -l | tr -d ' ')
  db=$(sql "select count(*) from test_cases t join questions q on q.id=t.question_id where q.slug='$s' and not t.is_hidden")
  [ "$api" = "$db" ] || { echo "    mismatch for $s: api=$api db=$db"; BAD=$((BAD+1)); }
done
check "no problem exposes extra cases"            0 "$BAD"

echo "== diagnostic answer keys =="
check "diagnostic slug detail returns 404"      404 "$(code '/api/questions/diag-arrays-two-pointers')"
check "unknown slug returns 404"                404 "$(code '/api/questions/no-such-problem')"
check "diagnostic endpoint has no answer key"     0 "$(get /api/assessment/diagnostic | grep -ciE 'expectedOutput|hidden|inputData')"
check "no token returns 401"                    401 "$(curl -s -o /dev/null -w '%{http_code}' "$API/api/questions")"

echo; echo "Passed: $PASS   Failed: $FAIL"; [ "$FAIL" -eq 0 ]
