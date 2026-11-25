#!/usr/bin/env bash
# Talks to Judge0 directly (no Spring Boot) to find out why verify-phase3.sh fails.
# Run from Git Bash:  bash docs/diagnose-judge0.sh
JUDGE0="${JUDGE0_URL:-http://localhost:2358}"
J='Content-Type: application/json'

echo "== 1. Judge0 reachable? =="
curl -s -o /dev/null -w 'GET /languages/62 -> HTTP %{http_code}\n' "$JUDGE0/languages/62"

echo; echo "== 2. Hello world in Java (language 62), waiting for the result =="
curl -s -X POST "$JUDGE0/submissions?base64_encoded=false&wait=true" -H "$J" \
  -d '{"language_id":62,"source_code":"public class Main { public static void main(String[] a){ System.out.println(\"hi\"); } }","stdin":"","expected_output":"hi"}'
echo
echo
echo "Reading the result:"
echo '  "status":{"id":3,...}            -> Judge0 works; the problem is in the Spring side (send me the app log).'
echo '  "status":{"id":13,...}           -> Judge0 sandbox cannot start. Look at "message" and the logs below.'
echo '  "Failed to create control group" -> host uses cgroup v2; Judge0 1.13.1 cannot sandbox on it.'
echo '  no JSON / HTTP 5xx / hangs       -> the worker is not running; see the worker log below.'

echo; echo "== 3. Host cgroup version (v2 prints cgroup2fs, v1 prints tmpfs) =="
docker exec codepath-judge0 sh -c 'stat -fc %T /sys/fs/cgroup' 2>&1

echo; echo "== 4. Last worker log lines =="
docker logs --tail 25 codepath-judge0-worker 2>&1
