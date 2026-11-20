#!/usr/bin/env bash
# End-to-end check of the device controls through HTTP, the same way the dashboard sends them.
#
# Start the app first (mvn jetty:run), then run:
#   HS_USER=<username> HS_PASSWORD=<password> bash scripts/http-smoke-test.sh [base-url]
# Default base URL: http://localhost:8080/homesmart. Works with MySQL or the sample data.
# The account must already exist (see "Sign-in and remote access" in the README).
# Uses device 1 (light), 2 (thermostat) and 3 (door lock), and changes their states.
#
# Each case posts a command, follows the redirect like a browser, and checks the HTTP codes and the
# message the dashboard shows. Exit code 0 = every case passed.

BASE="${1:-http://localhost:8080/homesmart}"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT
pass=0
fail=0

# The one-time message at the top of the dashboard (success or error alert)
flash_message() {
    grep -oE '<div class="alert alert-(success|error)"[^>]*>[^<]*' <<<"$1" | sed -E 's/<[^>]*>//' | head -1
}

# The _csrf form field value of a page
csrf_token() {
    curl -s -c "$JAR" -b "$JAR" "$1" | sed -nE 's/.*name="_csrf" value="([^"]*)".*/\1/p' | head -1
}

# Signs in with HS_USER and HS_PASSWORD, keeps the session cookie in the jar and reads the dashboard's token
sign_in() {
    curl -s -o /dev/null -c "$JAR" -b "$JAR" --data-urlencode "_csrf=$(csrf_token "$BASE/login")" \
        --data-urlencode "username=$HS_USER" --data-urlencode "password=$HS_PASSWORD" "$BASE/login"
    CSRF=$(csrf_token "$BASE/devices")
    [ -n "$CSRF" ]
}

# check <label> <expected message text> <deviceId> <action> [value]
check() {
    local label="$1" expected="$2" id="$3" action="$4"
    local args=(-s -o /dev/null -w '%{http_code}' -c "$JAR" -b "$JAR"
        --data-urlencode "_csrf=$CSRF" --data-urlencode "deviceId=$id" --data-urlencode "action=$action")
    [ $# -ge 5 ] && args+=(--data-urlencode "value=$5")

    local post page get msg
    post=$(curl "${args[@]}" "$BASE/devices")
    page=$(curl -s -c "$JAR" -b "$JAR" -w '\n%{http_code}' "$BASE/devices")
    get=$(tail -n1 <<<"$page")
    msg=$(flash_message "$page")

    if [ "$post" = 302 ] && [ "$get" = 200 ] && [[ "$msg" == *"$expected"* ]]; then
        pass=$((pass + 1)); printf 'PASS  %-38s %s\n' "$label" "$msg"
    else
        fail=$((fail + 1)); printf 'FAIL  %-38s POST %s, GET %s, message: "%s" (expected "%s")\n' \
            "$label" "$post" "$get" "$msg" "$expected"
    fi
}

# result <label> <condition is true?>
result() {
    if [ "$2" = true ]; then pass=$((pass + 1)); printf 'PASS  %s\n' "$1"
    else fail=$((fail + 1)); printf 'FAIL  %s\n' "$1"; fi
}

if ! curl -s -o /dev/null "$BASE/login"; then
    echo "Cannot reach $BASE - start the app with: mvn jetty:run"; exit 2
fi
if [ -z "$HS_USER" ] || [ -z "$HS_PASSWORD" ] || ! sign_in; then
    echo "Sign-in failed - set HS_USER and HS_PASSWORD to an existing account"; exit 2
fi
echo "Testing $BASE"
echo

echo "Light"
check "turn on"                      "Turned on"                         1 on
check "turn off"                     "Turned off"                        1 off
check "toggle"                       "Turned on"                         1 toggle

echo; echo "Thermostat - valid commands"
check "set 23"                       "Set temperature to 23 °C"          2 set 23
check "set ' 18 ' (spaces trimmed)"  "Set temperature to 18 °C"          2 set " 18 "
check "increase"                     "Set temperature to 19 °C"          2 increase
check "decrease"                     "Set temperature to 18 °C"          2 decrease
check "Eco mode preset"              "Eco mode: set temperature to 20 °C" 2 eco
check "Eco mode again"               "Eco mode: set temperature to 20 °C" 2 eco
check "set 10 (minimum)"             "Set temperature to 10 °C"          2 set 10
check "set 32 (maximum)"             "Set temperature to 32 °C"          2 set 32

echo; echo "Thermostat - rejected commands"
check "increase at 32"               "already at the maximum of 32 °C"   2 increase
check "set 10, then decrease"        "Set temperature to 10 °C"          2 set 10
check "decrease at 10"               "already at the minimum of 10 °C"   2 decrease
check "set 40"                       "between 10 and 32 °C"              2 set 40
check "set -5"                       "between 10 and 32 °C"              2 set -5
check "set 99999999999"              "between 10 and 32 °C"              2 set 99999999999
check "set 21.5"                     "must be a whole number"            2 set 21.5
check "set warm"                     "must be a whole number"            2 set warm
check "set (empty)"                  "Enter a target temperature"        2 set ""
check "set (no value)"               "Enter a target temperature"        2 set

echo; echo "Door lock"
check "unlock"                       "Unlocked"                          3 unlock
check "lock"                         "Locked"                            3 lock

echo; echo "Invalid requests"
check "eco on the light"             "does not support this action"      1 eco
check "set 'warm' on the light"      "does not support this action"      1 set warm
check "toggle the thermostat"        "does not support this action"      2 toggle
check "lock the light"               "does not support this action"      1 lock
check "unknown device 99"            "No device with id 99"              99 on
check "device id 'abc'"              "Invalid device id"                 abc on
check "unknown action"               "Unknown action: heat"              2 heat
check "no action"                    "No action was given"               2 ""
check "script in action is escaped"  "Unknown action: &lt;script&gt;"     2 "<script>alert(1)</script>"

echo; echo "Pages"
page=$(curl -s -c "$JAR" -b "$JAR" "$BASE/devices")
result "refresh after a command shows no message again (Post/Redirect/Get)" \
    "$([ -z "$(flash_message "$page")" ] && echo true)"
result "dashboard shows the Eco mode button" \
    "$(grep -q 'value="eco"' <<<"$page" && echo true)"
missing=$(curl -s -b "$JAR" -w '\n%{http_code}' "$BASE/no-such-page")
result "unknown page: friendly 404 page without Java details" \
    "$([ "$(tail -n1 <<<"$missing")" = 404 ] && grep -q 'Page not found' <<<"$missing" \
        && ! grep -qi 'exception' <<<"$missing" && echo true)"

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
