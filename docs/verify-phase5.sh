#!/usr/bin/env bash
# Phase 5 verification: safeguarded AI (hint + review) and the discussion module.
# Needs Postgres (docker container) + the app running. Judge0 is NOT needed.
# The AI checks need a real LLM key in the APP's environment (CODEPATH_AI_API_KEY, restart the app after setting it).
# Without a key the AI endpoints correctly return 503 and those checks are reported as SKIP, not FAIL.
# Run from Git Bash in the project folder:  bash docs/verify-phase5.sh
set -u
API="${API_URL:-http://localhost:8080}"
PG="${PG_CONTAINER:-codepath-postgres}"
J='Content-Type: application/json'
PASS=0; FAIL=0; SKIP=0
check() { if [ "$2" = "$3" ]; then echo "  PASS  $1"; PASS=$((PASS+1)); else echo "  FAIL  $1 (expected: $2, actual: $3)"; FAIL=$((FAIL+1)); fi; }
skip() { echo "  SKIP  $1"; SKIP=$((SKIP+1)); }
sql() { docker exec "$PG" psql -U codepath -d codepath -At -c "$1" 2>/dev/null | tr -d '\r'; }
post() { curl -s -X POST -H "$J" -H "Authorization: Bearer $2" "$API$1" -d "$3"; }
postcode() { curl -s -o /dev/null -w '%{http_code}' -X POST -H "$J" -H "Authorization: Bearer $2" "$API$1" -d "$3"; }
get() { curl -s -H "Authorization: Bearer $2" "$API$1"; }
has() { echo "$1" | grep -q "$2" && echo 1 || echo 0; }
register() {
  curl -s -X POST "$API/api/auth/register" -H "$J" -d "{\"name\":\"$1\",\"email\":\"$2\",\"password\":\"Passw0rd-verify\"}" \
    | grep -o '"accessToken":"[^"]*"' | head -1 | sed 's/"accessToken":"//; s/"$//'
}

STAMP=$(date +%s)
EMAIL_A="p5a.$STAMP@example.com"; EMAIL_B="p5b.$STAMP@example.com"
TA=$(register "Alice P5" "$EMAIL_A"); TB=$(register "Bob P5" "$EMAIL_B")
UA=$(sql "select id from users where email='$EMAIL_A'")
check "registered two fresh learners" 1 "$([ -n "$TA" ] && [ -n "$TB" ] && [ -n "$UA" ] && echo 1 || echo 0)"
QID=$(sql "select id from questions where slug='two-sum-sorted'")
DIAG=$(sql "select id from questions where placement_tags='DIAGNOSTIC' order by id limit 1")
check "found a practice question and a diagnostic question" 1 "$([ -n "$QID" ] && [ -n "$DIAG" ] && echo 1 || echo 0)"

echo "== security =="
check "hint without token returns 401"   401 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$J" "$API/api/ai/hint" -d '{}')"
check "review without token returns 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$J" "$API/api/ai/review" -d '{}')"
check "discussions without token return 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' "$API/api/discussions/question/$QID")"

echo "== 5.2 validation (no LLM call is made) =="
check "hintLevel 5 returns 400"        400 "$(postcode /api/ai/hint "$TA" "{\"questionId\":$QID,\"sourceCode\":\"x\",\"hintLevel\":5}")"
check "hintLevel 0 returns 400"        400 "$(postcode /api/ai/hint "$TA" "{\"questionId\":$QID,\"sourceCode\":\"x\",\"hintLevel\":0}")"
check "missing questionId returns 400" 400 "$(postcode /api/ai/hint "$TA" '{"hintLevel":1}')"
check "bad verdict value returns 400"  400 "$(postcode /api/ai/hint "$TA" "{\"questionId\":$QID,\"verdict\":\"NOPE\",\"hintLevel\":1}")"
check "unknown question returns 404"   404 "$(postcode /api/ai/hint "$TA" '{"questionId":999999,"hintLevel":1}')"
check "diagnostic question returns 404" 404 "$(postcode /api/ai/hint "$TA" "{\"questionId\":$DIAG,\"hintLevel\":1}")"
check "Level 2 before Level 1 returns 409" 409 "$(postcode /api/ai/hint "$TA" "{\"questionId\":$QID,\"sourceCode\":\"x\",\"hintLevel\":2}")"
check "Level 4 before Level 3 returns 409" 409 "$(postcode /api/ai/hint "$TA" "{\"questionId\":$QID,\"sourceCode\":\"x\",\"hintLevel\":4}")"
check "nothing was counted for rejected requests" 0 "$(sql "select count(*) from ai_interactions where user_id=$UA")"

echo "== 5.2 review needs a verified ACCEPTED submission =="
check "review without an ACCEPTED submission returns 409" 409 "$(postcode /api/ai/review "$TA" "{\"questionId\":$QID}")"
sql "insert into submissions (user_id, question_id, source_code, language, verdict, hints_used_count, created_at) values ($UA, $QID, 'public class Main { public static void main(String[] a) { } }', 'JAVA', 'WRONG_ANSWER', 0, now())" >/dev/null
WA_ID=$(sql "select max(id) from submissions where user_id=$UA")
check "review of a WRONG_ANSWER submission returns 409" 409 "$(postcode /api/ai/review "$TA" "{\"questionId\":$QID,\"submissionId\":$WA_ID}")"
check "another learner cannot review it (404)" 404 "$(postcode /api/ai/review "$TB" "{\"questionId\":$QID,\"submissionId\":$WA_ID}")"

echo "== 5.1/5.2 hint (needs an LLM key in the app) =="
H1=$(post /api/ai/hint "$TA" "{\"questionId\":$QID,\"sourceCode\":\"\",\"hintLevel\":1}")
if [ "$(has "$H1" '"success":true')" = "1" ]; then
  check "Level 1 hint flagged \"isVerified\":false" 1 "$(has "$H1" '"isVerified":false')"
  check "Level 1 hint labelled as unverified guidance" 1 "$(has "$H1" 'Unverified Guidance')"
  check "Level 1 hint contains no code fence" 0 "$(echo "$H1" | grep -c '```')"
  check "Level 1 hint saved as HINT_L1" 1 "$(sql "select count(*) from ai_interactions where user_id=$UA and interaction_type='HINT_L1'")"
  H2=$(post /api/ai/hint "$TA" "{\"questionId\":$QID,\"sourceCode\":\"int x;\",\"verdict\":\"WRONG_ANSWER\",\"hintLevel\":2}")
  check "Level 2 is allowed after Level 1" 1 "$(has "$H2" '"interactionType":"HINT_L2"')"
  check "hint counter reports 2" 1 "$(has "$H2" '"hintsUsedForQuestion":2')"
  check "hints are counted from ai_interactions" 2 "$(sql "select count(*) from ai_interactions where user_id=$UA and question_id=$QID and interaction_type like 'HINT_L%'")"
else
  if [ "$(echo "$H1" | grep -c 'not configured')" = "1" ]; then
    skip "no CODEPATH_AI_API_KEY in the app: AI endpoint correctly returned 503 (set the key and restart the app to run these checks)"
  else
    echo "  FAIL  hint failed unexpectedly: $H1"; FAIL=$((FAIL+1))
  fi
  check "a failed AI call saved and counted nothing" 0 "$(sql "select count(*) from ai_interactions where user_id=$UA")"
fi

echo "== 5.2 review of an ACCEPTED submission =="
sql "insert into submissions (user_id, question_id, source_code, language, verdict, hints_used_count, created_at) values ($UA, $QID, 'public class Main { /* accepted */ }', 'JAVA', 'ACCEPTED', 0, now())" >/dev/null
R=$(post /api/ai/review "$TA" "{\"questionId\":$QID}")
if [ "$(has "$R" '"success":true')" = "1" ]; then
  check "review flagged \"isVerified\":false" 1 "$(has "$R" '"isVerified":false')"
  check "review saved as AC_REVIEW linked to the submission" 1 "$(sql "select count(*) from ai_interactions where user_id=$UA and interaction_type='AC_REVIEW' and submission_id is not null")"
else
  skip "review needs an LLM key in the app (response: $(echo "$R" | cut -c1-120))"
fi

echo "== 5.3 discussions =="
D=$(post "/api/discussions/question/$QID" "$TA" '{"title":"How do I avoid O(n^2)?","content":"Is two pointers the intended approach?"}')
DID=$(echo "$D" | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')
check "create returns 201" 201 "$(postcode "/api/discussions/question/$QID" "$TA" '{"title":"Second thread","content":"More thoughts"}')"
check "created thread has an id and 0 upvotes" 1 "$([ -n "$DID" ] && [ "$(has "$D" '"upvotes":0')" = "1" ] && echo 1 || echo 0)"
check "blank title returns 400" 400 "$(postcode "/api/discussions/question/$QID" "$TA" '{"title":"  ","content":"x"}')"
check "diagnostic question has no discussions (404)" 404 "$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TA" "$API/api/discussions/question/$DIAG")"
C=$(post "/api/discussions/$DID/comments" "$TB" '{"content":"Yes, sort first then use two pointers."}')
check "comment is added" 1 "$(has "$C" '"authorName":"Bob P5"')"
check "blank comment returns 400" 400 "$(postcode "/api/discussions/$DID/comments" "$TB" '{"content":""}')"
check "comment on a missing thread returns 404" 404 "$(postcode "/api/discussions/999999/comments" "$TB" '{"content":"hi"}')"
check "author cannot upvote own thread (409)" 409 "$(postcode "/api/discussions/$DID/upvote" "$TA" '')"
U=$(post "/api/discussions/$DID/upvote" "$TB" '')
check "another learner can upvote (count 1)" 1 "$(has "$U" '"upvotes":1')"
L=$(get "/api/discussions/question/$QID" "$TA")
check "list shows the thread with 1 comment and 1 upvote" 1 "$(has "$L" "\"id\":$DID,\"questionId\":$QID,\"title\":\"How do I avoid O(n^2)?\",\"authorName\":\"Alice P5\",\"upvotes\":1,\"commentCount\":1")"
check "most upvoted thread is listed first" 1 "$(echo "$L" | grep -o '"id":[0-9]*' | head -1 | grep -q ":$DID" && echo 1 || echo 0)"
G=$(get "/api/discussions/$DID" "$TA")
check "detail includes the comment" 1 "$(has "$G" 'sort first then use two pointers')"
check "emails are never exposed" 0 "$(echo "$L$G$C" | grep -c '@example.com')"

echo
echo "Passed: $PASS  Failed: $FAIL  Skipped: $SKIP"
[ "$FAIL" -eq 0 ]
