#!/usr/bin/env bash
# Phase 4 verification: recommendations (with reasons, prerequisite-valid), roadmap, revision queue, analytics.
# Needs Postgres (docker container) + the app running. Judge0 is NOT needed.
# Run from Git Bash in the project folder:  bash docs/verify-phase4.sh
set -u
API="${API_URL:-http://localhost:8080}"
PG="${PG_CONTAINER:-codepath-postgres}"
J='Content-Type: application/json'
PASS=0; FAIL=0
check() { if [ "$2" = "$3" ]; then echo "  PASS  $1"; PASS=$((PASS+1)); else echo "  FAIL  $1 (expected: $2, actual: $3)"; FAIL=$((FAIL+1)); fi; }
sql() { docker exec "$PG" psql -U codepath -d codepath -At -c "$1" 2>/dev/null | tr -d '\r'; }
get() { curl -s -H "Authorization: Bearer $TOKEN" "$API$1"; }
code() { curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$API$1"; }
count() { grep -o "$1" | wc -l | tr -d ' '; }
slugs() { grep -o '"slug":"[^"]*"' | sed 's/"slug":"//; s/"$//'; }

EMAIL="p4.$(date +%s)@example.com"
TOKEN=$(curl -s -X POST "$API/api/auth/register" -H "$J" -d "{\"name\":\"P4\",\"email\":\"$EMAIL\",\"password\":\"Passw0rd-verify\"}" \
  | grep -o '"accessToken":"[^"]*"' | head -1 | sed 's/"accessToken":"//; s/"$//')
UID_DB=$(sql "select id from users where email='$EMAIL'")
check "registered a fresh learner" 1 "$([ -n "$UID_DB" ] && echo 1 || echo 0)"

echo "== security =="
for p in /api/learning/recommendations /api/roadmap /api/revision/queue /api/analytics/summary; do
  check "no token returns 401 ($p)" 401 "$(curl -s -o /dev/null -w '%{http_code}' "$API$p")"
done

echo "== 4.2 recommendations: learner with no mastery =="
R1=$(get /api/learning/recommendations?limit=10)
check "returns 200" 200 "$(code '/api/learning/recommendations')"
N1=$(echo "$R1" | slugs | wc -l | tr -d ' ')
check "returns at least one recommendation" 1 "$([ "$N1" -gt 0 ] && echo 1 || echo 0)"
BAD=0
for s in $(echo "$R1" | slugs); do
  t=$(sql "select t.name from questions q join topics t on t.id=q.topic_id where q.slug='$s'")
  [ "$t" = "Arrays" ] || { echo "    $s belongs to $t (should be locked)"; BAD=$((BAD+1)); }
done
check "only root-topic (Arrays) problems are recommended" 0 "$BAD"
check "every recommendation has a reason"  "$N1" "$(echo "$R1" | count '"reason":"Recommended because ')"
check "locked topics are listed with a reason" 1 "$(echo "$R1" | grep -q 'Locked until Arrays reaches 40%' && echo 1 || echo 0)"
check "no diagnostic questions recommended" 0 "$(echo "$R1" | grep -c 'diag-')"
check "limit=2 returns 2" 2 "$(get '/api/learning/recommendations?limit=2' | slugs | wc -l | tr -d ' ')"

echo "== 4.2 recommendations: after mastery changes (Arrays 75, Strings 55) =="
sql "insert into topic_mastery (user_id, topic_id, mastery_score, status, attempts_count, solved_count) select $UID_DB, id, 75, 'STRONG', 0, 0 from topics where name='Arrays'" >/dev/null
sql "insert into topic_mastery (user_id, topic_id, mastery_score, status, attempts_count, solved_count) select $UID_DB, id, 55, 'DEVELOPING', 0, 0 from topics where name='Strings'" >/dev/null
R2=$(get /api/learning/recommendations?limit=20)
check "recommendations differ from the no-mastery learner" 1 "$([ "$(echo "$R1" | slugs | tr '\n' ',')" != "$(echo "$R2" | slugs | tr '\n' ',')" ] && echo 1 || echo 0)"
BAD=0
for s in $(echo "$R2" | slugs); do
  unmet=$(sql "select count(*) from questions q join topic_prerequisites p on p.topic_id=q.topic_id
               left join topic_mastery m on m.topic_id=p.prerequisite_topic_id and m.user_id=$UID_DB
               where q.slug='$s' and (m.id is null or m.mastery_score < 40 or m.status='UNASSESSED')")
  [ "$unmet" = "0" ] || { echo "    $s has $unmet unmet prerequisites"; BAD=$((BAD+1)); }
done
check "every recommended problem has all prerequisites met" 0 "$BAD"
check "Strings problems are now recommended" 1 "$(for s in $(echo "$R2" | slugs); do sql "select t.name from questions q join topics t on t.id=q.topic_id where q.slug='$s'"; done | grep -q '^Strings$' && echo 1 || echo 0)"

echo "== 4.3 roadmap =="
RM=$(get /api/roadmap)
check "returns 200" 200 "$(code /api/roadmap)"
check "has 5 topic steps" 5 "$(echo "$RM" | count '"lockedReason"')"
check "first step is Arrays" 1 "$(echo "$RM" | grep -q '"order":1,"topicId":[0-9]*,"topic":"Arrays"' && echo 1 || echo 0)"
check "lists 15 problems in total" 15 "$(echo "$RM" | slugs | wc -l | tr -d ' ')"
check "marks the roadmap as optional" 1 "$(echo "$RM" | grep -q 'optional' && echo 1 || echo 0)"

echo "== 4.4 revision queue =="
check "returns 200" 200 "$(code /api/revision/queue)"
check "empty before anything is due" 1 "$(get /api/revision/queue | grep -q '"dueCount":0' && echo 1 || echo 0)"
sql "update topic_mastery set next_revision_due = now() - interval '2 days' where user_id=$UID_DB and topic_id=(select id from topics where name='Arrays')" >/dev/null
RV=$(get /api/revision/queue)
check "overdue topic appears" 1 "$(echo "$RV" | grep -q '"topic":"Arrays"' && echo 1 || echo 0)"
check "trigger is SCHEDULED" 1 "$(echo "$RV" | grep -q '"trigger":"SCHEDULED"' && echo 1 || echo 0)"
check "reason explains the overdue date" 1 "$(echo "$RV" | grep -q 'revision was due 2 days ago' && echo 1 || echo 0)"

echo "== 4.5 analytics =="
AN=$(get /api/analytics/summary)
check "returns 200" 200 "$(code /api/analytics/summary)"
check "5 topics in mastery radar data" 5 "$(echo "$AN" | count '"masteryScore"')"
check "3 difficulty rows" 3 "$(echo "$AN" | count '"difficulty":"\(EASY\|MEDIUM\|HARD\)"')"
check "verdict distribution is zero-filled (5 verdicts)" 5 "$(echo "$AN" | count '"verdict":"')"
check "mastery estimate note present" 1 "$(echo "$AN" | grep -q 'rule-based estimate' && echo 1 || echo 0)"

echo; echo "Passed: $PASS   Failed: $FAIL"; [ "$FAIL" -eq 0 ]
