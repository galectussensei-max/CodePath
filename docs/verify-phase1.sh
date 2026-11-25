#!/usr/bin/env bash
# Phase 1 verification for CodePath.
#
# Before running:
#   1. docker compose up -d postgres          (from the project root)
#   2. cd backend && mvn spring-boot:run      (leave it running; wait for "Seeded 5 topics ...")
# Then, from the project root:
#   bash docs/verify-phase1.sh
#
# Needs: bash, curl, docker (to query the Postgres container). Override with API_URL / PG_CONTAINER.

set -u

API_URL="${API_URL:-http://localhost:8080}"
PG_CONTAINER="${PG_CONTAINER:-codepath-postgres}"
ORIGIN="${ORIGIN:-http://localhost:5173}"

PASS=0
FAIL=0
TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY"' EXIT

check() {
  local name="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "  PASS  $name"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  $name (expected: $expected, actual: $actual)"
    FAIL=$((FAIL + 1))
  fi
}

sql() {
  docker exec "$PG_CONTAINER" psql -U codepath -d codepath -At -c "$1" 2>/dev/null | tr -d '[:space:]'
}

http_status() {
  # usage: http_status METHOD PATH [extra curl args...]; the response body is left in $TMP_BODY
  local method="$1" path="$2"
  shift 2
  curl -s -o "$TMP_BODY" -w '%{http_code}' -X "$method" "$API_URL$path" "$@"
}

extract_token() {
  grep -o '"accessToken":"[^"]*"' "$TMP_BODY" | head -1 | sed 's/"accessToken":"//; s/"$//'
}

echo "== 1. Seed data in PostgreSQL =="
check "topics = 5"                       5  "$(sql 'select count(*) from topics')"
check "topic_prerequisites = 6"          6  "$(sql 'select count(*) from topic_prerequisites')"
check "concepts = 10"                    10 "$(sql 'select count(*) from concepts')"
check "problems (non-diagnostic) = 15"   15 "$(sql "select count(*) from questions where placement_tags <> 'DIAGNOSTIC'")"
check "diagnostic questions = 10"        10 "$(sql "select count(*) from questions where placement_tags = 'DIAGNOSTIC'")"
check "test_cases = 94"                  94 "$(sql 'select count(*) from test_cases')"
check "every problem has a hidden case"  0  "$(sql "select count(*) from questions q where q.placement_tags <> 'DIAGNOSTIC' and not exists (select 1 from test_cases t where t.question_id = q.id and t.is_hidden)")"
check "every problem has a public case"  0  "$(sql "select count(*) from questions q where q.placement_tags <> 'DIAGNOSTIC' and not exists (select 1 from test_cases t where t.question_id = q.id and not t.is_hidden)")"
check "diagnostic answer keys all hidden" 0 "$(sql "select count(*) from test_cases t join questions q on q.id = t.question_id where q.placement_tags = 'DIAGNOSTIC' and not t.is_hidden")"
check "Strings requires Arrays"          1  "$(sql "select count(*) from topic_prerequisites p join topics t on t.id = p.topic_id join topics r on r.id = p.prerequisite_topic_id where t.name = 'Strings' and r.name = 'Arrays'")"

echo "== 2. Authentication =="
EMAIL="verify.$(date +%s)@example.com"
PASSWORD="Passw0rd-verify"

status="$(http_status POST /api/auth/register -H 'Content-Type: application/json' \
  -d "{\"name\":\"Verify User\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
check "register returns 201" 201 "$status"
check "register returns a token" 1 "$([ -n "$(extract_token)" ] && echo 1 || echo 0)"

status="$(http_status POST /api/auth/register -H 'Content-Type: application/json' \
  -d "{\"name\":\"Verify User\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
check "duplicate email returns 409" 409 "$status"

status="$(http_status POST /api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"X","email":"not-an-email","password":"short"}')"
check "invalid registration returns 400" 400 "$status"

status="$(http_status POST /api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
check "login returns 200" 200 "$status"
TOKEN="$(extract_token)"
check "login returns a JWT (3 dot-separated parts)" 3 "$(echo "$TOKEN" | awk -F. '{print NF}')"
check "login response reports diagnosticCompleted=false" 1 "$(grep -c '"diagnosticCompleted":false' "$TMP_BODY")"

status="$(http_status POST /api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"wrong-password\"}")"
check "wrong password returns 401" 401 "$status"

status="$(http_status POST /api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"  $(echo "$EMAIL" | tr '[:lower:]' '[:upper:]')  \",\"password\":\"$PASSWORD\"}")"
check "login is case/whitespace-insensitive on email" 200 "$status"

check "registered user stored as STUDENT with a profile" 1 \
  "$(sql "select count(*) from users u join learner_profiles p on p.user_id = u.id where u.email = '$EMAIL' and u.role = 'STUDENT' and p.diagnostic_completed = false")"
check "password is stored as a BCrypt hash" 1 \
  "$(sql "select count(*) from users where email = '$EMAIL' and password_hash like '\$2%'")"

echo "== 3. JWT filter and CORS =="
status="$(http_status GET /api/does-not-exist)"
check "protected route without token returns 401" 401 "$status"

status="$(http_status GET /api/does-not-exist -H "Authorization: Bearer ${TOKEN}tampered")"
check "tampered token returns 401" 401 "$status"

status="$(http_status GET /api/does-not-exist -H "Authorization: Bearer $TOKEN")"
check "valid token passes the filter (404 = authenticated, route not built yet)" 404 "$status"

headers="$(curl -s -D - -o /dev/null -X OPTIONS "$API_URL/api/auth/login" \
  -H "Origin: $ORIGIN" -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: authorization,content-type')"
check "CORS preflight allows $ORIGIN" 1 \
  "$(echo "$headers" | tr -d '\r' | grep -ci "^access-control-allow-origin: $ORIGIN$")"

echo
echo "Passed: $PASS   Failed: $FAIL"
[ "$FAIL" -eq 0 ]
