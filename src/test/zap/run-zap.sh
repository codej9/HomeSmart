#!/usr/bin/env bash
# Signs in with curl, checks /devices, then runs a copy of the ZAP plan with the session id filled in.
set -euo pipefail

: "${HS_EVIDENCE_PASSWORD:?set HS_EVIDENCE_PASSWORD}"
: "${HS_ZAP_REPORT_DIR:?set HS_ZAP_REPORT_DIR}"
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
base=http://127.0.0.1:8083/homesmart
jar=$(mktemp)
plan=$(mktemp --suffix=.yaml)
trap 'rm -f "$jar" "$plan"' EXIT

csrf=$(curl -fsS -c "$jar" -b "$jar" "$base/login" | sed -n 's/.*name="_csrf" value="\([^"]*\)".*/\1/p' | head -n 1)
curl -sS -o /dev/null -c "$jar" -b "$jar" \
  --data-urlencode "_csrf=$csrf" \
  --data-urlencode username=evidence \
  --data-urlencode "password=$HS_EVIDENCE_PASSWORD" \
  "$base/login"
HS_ZAP_SESSION=$(awk '$6 == "JSESSIONID" { print $7 }' "$jar")
[ -n "$HS_ZAP_SESSION" ] || { echo "run-zap: login set no JSESSIONID" >&2; exit 1; }

page=$(curl -sS -w '\n%{http_code}' -b "JSESSIONID=$HS_ZAP_SESSION" "$base/devices")
code=${page##*$'\n'}
if [ "$code" != 200 ] || ! grep -q 'Log out' <<<"$page"; then
  echo "run-zap: /devices with session returned HTTP $code without Log out" >&2
  exit 1
fi

mkdir -p "$HS_ZAP_REPORT_DIR"
sed "s|\${HS_ZAP_SESSION}|$HS_ZAP_SESSION|" "$here/homesmart-plan.yaml" > "$plan"
zap -cmd -autorun "$plan"
