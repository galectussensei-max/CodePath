#!/usr/bin/env bash
# Phase 3 verification: Judge0 verdicts (Accepted, Wrong Answer, Compilation Error, Runtime Error, TLE),
# RUN vs SUBMIT behaviour, hidden-case secrecy and mastery updates.
# Needs Postgres, Judge0 (docker compose up -d) and the Spring Boot app running.
# Run from Git Bash:  bash docs/verify-phase3.sh
set -u
API="${API_URL:-http://localhost:8080}"
JUDGE0="${JUDGE0_URL:-http://localhost:2358}"
PG="${PG_CONTAINER:-codepath-postgres}"
J='Content-Type: application/json'
PASS=0; FAIL=0
check() { if [ "$2" = "$3" ]; then echo "  PASS  $1"; PASS=$((PASS+1)); else echo "  FAIL  $1 (expected: $2, actual: $3)"; FAIL=$((FAIL+1)); fi; }
sql() { docker exec "$PG" psql -U codepath -d codepath -At -c "$1" 2>/dev/null | tr -d '[:space:]'; }
code() { curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" -H "$J" -X "$1" "$API$2" ${3:+-d "$3"}; }
post() { curl -s -H "Authorization: Bearer $TOKEN" -H "$J" -X POST "$API$1" -d "$2"; }
field() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | sed "s/\"$2\":\"//; s/\"\$//"; }
num() { echo "$1" | grep -o "\"$2\":[0-9.]*" | head -1 | sed "s/\"$2\"://"; }

if [ "$(curl -s -o /dev/null -w '%{http_code}' "$JUDGE0/languages/62")" != "200" ]; then
  echo "Judge0 is not reachable at $JUDGE0. Start it with: docker compose up -d (wait ~30s) and re-run."; exit 2
fi

EMAIL="p3.$(date +%s)@example.com"
TOKEN=$(curl -s -X POST "$API/api/auth/register" -H "$J" -d "{\"name\":\"P3\",\"email\":\"$EMAIL\",\"password\":\"Passw0rd-verify\"}" \
  | grep -o '"accessToken":"[^"]*"' | head -1 | sed 's/"accessToken":"//; s/"$//')
UID_DB=$(sql "select id from users where email='$EMAIL'")
QID=$(sql "select id from questions where slug='two-sum-sorted'")
TOPIC=$(sql "select topic_id from questions where id=$QID")
PUB=$(sql "select count(*) from test_cases where question_id=$QID and not is_hidden")
ALL=$(sql "select count(*) from test_cases where question_id=$QID")
DIAG=$(sql "select id from questions where placement_tags='DIAGNOSTIC' order by id limit 1")

# One-line Java sources (already JSON-escaped). Problem: sorted array, print 1-based pair summing to target.
AC='import java.util.*; public class Main { public static void main(String[] a){ Scanner s=new Scanner(System.in); int n=s.nextInt(); long t=s.nextLong(); long[] v=new long[n]; for(int i=0;i<n;i++) v[i]=s.nextLong(); int l=0,r=n-1; while(l<r){ long x=v[l]+v[r]; if(x==t){ System.out.println((l+1)+\" \"+(r+1)); return; } if(x<t) l++; else r--; } } }'
WA='public class Main { public static void main(String[] a){ System.out.println(\"0 0\"); } }'
CE='public class Main { public static void main(String[] a){ int x = ; } }'
RE='public class Main { public static void main(String[] a){ int[] v=new int[1]; System.out.println(v[5]); } }'
TLE='public class Main { public static void main(String[] a){ while(true){} } }'
body() { echo "{\"questionId\":$QID,\"sourceCode\":\"$1\"}"; }

echo "== auth and validation =="
check "run without token returns 401"        401 "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/submissions/run" -H "$J" -d "$(body "$AC")")"
check "blank source returns 400"             400 "$(code POST /api/submissions/run "{\"questionId\":$QID,\"sourceCode\":\"\"}")"
check "unknown question returns 404"         404 "$(code POST /api/submissions/run '{"questionId":999999,"sourceCode":"x"}')"
check "diagnostic question run returns 404"  404 "$(code POST /api/submissions/run "{\"questionId\":$DIAG,\"sourceCode\":\"x\"}")"
check "diagnostic question submit returns 404" 404 "$(code POST /api/submissions/submit "{\"questionId\":$DIAG,\"sourceCode\":\"x\"}")"
check "diagnostic history returns 404"       404 "$(code GET "/api/submissions/question/$DIAG")"

echo "== RUN (public cases only, nothing saved) =="
SUBS_BEFORE=$(sql "select count(*) from submissions where user_id=$UID_DB")
R=$(post /api/submissions/run "$(body "$AC")")
check "run: verdict ACCEPTED"                ACCEPTED "$(field "$R" verdict)"
check "run: mode is RUN"                     RUN "$(field "$R" mode)"
check "run: totalCount = public cases"       "$PUB" "$(num "$R" totalCount)"
check "run: passedCount = public cases"      "$PUB" "$(num "$R" passedCount)"
check "run: shows only public cases"         "$PUB" "$(echo "$R" | grep -o '"index"' | wc -l | tr -d ' ')"
check "run: no hidden case in response"      0 "$(echo "$R" | grep -c '"hidden":true')"
check "run: wrong answer detected"           WRONG_ANSWER "$(field "$(post /api/submissions/run "$(body "$WA")")" verdict)"
check "run: nothing was saved"               "$SUBS_BEFORE" "$(sql "select count(*) from submissions where user_id=$UID_DB")"
check "run: mastery untouched"               0 "$(sql "select count(*) from topic_mastery where user_id=$UID_DB")"

echo "== SUBMIT verdicts =="
S=$(post /api/submissions/submit "$(body "$AC")")
check "submit AC: verdict ACCEPTED"          ACCEPTED "$(field "$S" verdict)"
check "submit AC: runs public + hidden"      "$ALL" "$(num "$S" totalCount)"
check "submit AC: all cases passed"          "$ALL" "$(num "$S" passedCount)"
check "submit AC: hidden cases are listed"   $((ALL-PUB)) "$(echo "$S" | grep -o '"hidden":true' | wc -l | tr -d ' ')"
check "submit AC: only public expectedOutput shown" "$PUB" "$(echo "$S" | grep -o '"expectedOutput"' | wc -l | tr -d ' ')"
check "submit AC: has execution time"        1 "$([ -n "$(num "$S" executionTimeMs)" ] && echo 1 || echo 0)"
check "submit AC: saved in DB"               1 "$(sql "select count(*) from submissions where user_id=$UID_DB and verdict='ACCEPTED'")"
check "submit AC: mastery row created"       1 "$(sql "select count(*) from topic_mastery where user_id=$UID_DB and topic_id=$TOPIC")"
SCORE1=$(sql "select mastery_score from topic_mastery where user_id=$UID_DB and topic_id=$TOPIC")
check "submit AC: score rose to 8 (EASY first try)" 8 "$(echo "$SCORE1" | sed 's/\.0*$//')"
check "submit AC: solved_count is 1"         1 "$(sql "select solved_count from topic_mastery where user_id=$UID_DB and topic_id=$TOPIC")"
check "submit AC: response explains mastery change" 1 "$([ "$(echo "$S" | grep -c '"factors"')" -ge 1 ] && echo 1 || echo 0)"

check "submit WA: WRONG_ANSWER"              WRONG_ANSWER "$(field "$(post /api/submissions/submit "$(body "$WA")")" verdict)"
CE_RESP=$(post /api/submissions/submit "$(body "$CE")")
check "submit CE: COMPILATION_ERROR"         COMPILATION_ERROR "$(field "$CE_RESP" verdict)"
check "submit CE: compiler output returned"  1 "$([ "$(echo "$CE_RESP" | grep -c '"compileOutput"')" -ge 1 ] && echo 1 || echo 0)"
check "submit RE: RUNTIME_ERROR"             RUNTIME_ERROR "$(field "$(post /api/submissions/submit "$(body "$RE")")" verdict)"
echo "  (infinite loop test waits for the time limit...)"
check "submit TLE: TIME_LIMIT_EXCEEDED"      TIME_LIMIT_EXCEEDED "$(field "$(post /api/submissions/submit "$(body "$TLE")")" verdict)"

echo "== lifecycle and mastery rules =="
check "5 submissions saved"                  5 "$(sql "select count(*) from submissions where user_id=$UID_DB")"
check "history endpoint returns 5"           5 "$(curl -s -H "Authorization: Bearer $TOKEN" "$API/api/submissions/question/$QID" | grep -o '"verdict"' | wc -l | tr -d ' ')"
SCORE2=$(sql "select mastery_score from topic_mastery where user_id=$UID_DB and topic_id=$TOPIC")
check "later failures on a solved problem do not lower mastery" "$SCORE1" "$SCORE2"
post /api/submissions/submit "$(body "$AC")" > /dev/null
check "repeat AC does not raise mastery"     "$SCORE1" "$(sql "select mastery_score from topic_mastery where user_id=$UID_DB and topic_id=$TOPIC")"
check "solved_count stays 1"                 1 "$(sql "select solved_count from topic_mastery where user_id=$UID_DB and topic_id=$TOPIC")"
check "attempts_count counts all 6"          6 "$(sql "select attempts_count from topic_mastery where user_id=$UID_DB and topic_id=$TOPIC")"

echo; echo "Passed: $PASS   Failed: $FAIL"; [ "$FAIL" -eq 0 ]
