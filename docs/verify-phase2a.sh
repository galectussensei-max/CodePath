#!/usr/bin/env bash
# Verifies tasks 2.1 (profile) and 2.2 (diagnostic). Run from Git Bash with the app running.
API="${API_URL:-http://localhost:8080}"
EMAIL="p2a.$(date +%s)@example.com"
J='Content-Type: application/json'
tok() { grep -o '"accessToken":"[^"]*"' | head -1 | sed 's/"accessToken":"//; s/"$//'; }

TOKEN=$(curl -s -X POST "$API/api/auth/register" -H "$J" -d "{\"name\":\"P2A\",\"email\":\"$EMAIL\",\"password\":\"Passw0rd-verify\"}" | tok)
AUTH="Authorization: Bearer $TOKEN"

echo "--- PUT profile (expect 200, success:true)"
curl -s -w '\n%{http_code}\n' -X PUT "$API/api/users/profile" -H "$J" -H "$AUTH" \
  -d '{"programmingBackground":"Java basics","dsaExperience":"Beginner","prepGoal":"Campus placements","targetLevel":"Intermediate","dailyStudyMinutes":90}'
echo "--- PUT invalid profile (expect 400)"
curl -s -o /dev/null -w '%{http_code}\n' -X PUT "$API/api/users/profile" -H "$J" -H "$AUTH" -d '{"prepGoal":"","dailyStudyMinutes":1}'
echo "--- GET profile (expect 200)"
curl -s -w '\n%{http_code}\n' "$API/api/users/profile" -H "$AUTH"

echo "--- GET diagnostic (expect 10 questions, NO answer key)"
BODY=$(curl -s "$API/api/assessment/diagnostic" -H "$AUTH")
echo "questions: $(echo "$BODY" | grep -o '"prompt"' | wc -l)  (expect 10)"
echo "leaks (expectedOutput/hidden/inputData): $(echo "$BODY" | grep -ciE 'expectedOutput|hidden|inputData')  (expect 0)"

IDS=$(echo "$BODY" | grep -o '"id":[0-9]*' | sed 's/"id"://')
ANS=$(for i in $IDS; do printf '{"questionId":%s,"answer":"A"},' "$i"; done | sed 's/,$//')
echo "--- POST submit, all 'A' (expect 200 + topic mastery)"
curl -s -w '\n%{http_code}\n' -X POST "$API/api/assessment/submit" -H "$J" -H "$AUTH" -d "{\"answers\":[$ANS]}"
echo "--- POST submit again (expect 409)"
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$API/api/assessment/submit" -H "$J" -H "$AUTH" -d "{\"answers\":[$ANS]}"
echo "--- no token (expect 401)"
curl -s -o /dev/null -w '%{http_code}\n' "$API/api/assessment/diagnostic"
