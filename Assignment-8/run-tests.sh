#!/usr/bin/env bash
# Deploys the Exercise 8 models and runs the three test cases via the Camunda 8 REST API (v2).
#
# SaaS: export the variables from your cluster's API client credentials file
#   (ZEEBE_REST_ADDRESS, ZEEBE_CLIENT_ID, ZEEBE_CLIENT_SECRET,
#    ZEEBE_AUTHORIZATION_SERVER_URL, ZEEBE_TOKEN_AUDIENCE).
# Local (C8 Run / docker-compose): only ZEEBE_REST_ADDRESS is needed, default http://localhost:8080.
#
# Required for every run: CHAT_ID = the Telegram chat_id that will receive the messages.
#
# Usage: CHAT_ID=123456789 ./run-tests.sh [deploy|success|full|cancel|all] [process-id]
#   process-id defaults to appointment-booking; use appointment-booking-propagate-all for the comparison.
set -euo pipefail

cd "$(dirname "$0")"
BASE="${ZEEBE_REST_ADDRESS:-http://localhost:8080}"
BASE="${BASE%/}/v2"
CHAT_ID="${CHAT_ID:?Set CHAT_ID to your Telegram chat_id}"
STEP="${1:-all}"
PROCESS_ID="${2:-appointment-booking}"

AUTH=()
if [[ -n "${ZEEBE_CLIENT_ID:-}" ]]; then
  TOKEN=$(curl -sf -X POST "${ZEEBE_AUTHORIZATION_SERVER_URL:-https://login.cloud.camunda.io/oauth/token}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode grant_type=client_credentials \
    --data-urlencode "client_id=$ZEEBE_CLIENT_ID" \
    --data-urlencode "client_secret=$ZEEBE_CLIENT_SECRET" \
    --data-urlencode "audience=${ZEEBE_TOKEN_AUDIENCE:-zeebe.camunda.io}" |
    python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
  AUTH=(-H "Authorization: Bearer $TOKEN")
fi

api() { # api METHOD PATH [JSON]
  curl -sS -X "$1" "$BASE$2" "${AUTH[@]}" -H 'Content-Type: application/json' ${3:+-d "$3"}
  echo
}

deploy() {
  echo "== Deploying models"
  curl -sS -X POST "$BASE/deployments" "${AUTH[@]}" \
    -F resources=@send-notification.bpmn \
    -F resources=@slot_decision.dmn \
    -F resources=@appointment-booking.bpmn \
    -F resources=@appointment-booking-propagate-all.bpmn
  echo
}

start() { # start PATIENT_ID BOOKED_COUNT
  api POST /process-instances "{
    \"processDefinitionId\": \"$PROCESS_ID\",
    \"variables\": {
      \"patientId\": \"$1\",
      \"telegramChatId\": \"$CHAT_ID\",
      \"patientName\": \"Om Pathania\",
      \"department\": \"Cardiology\",
      \"preferredSlot\": \"2026-11-02 10:30\",
      \"bookedCount\": $2
    }
  }"
}

success() { echo "== Test 1: Cardiology, bookedCount = 3 (booking succeeds)"; start P-1001 3; }
full()    { echo "== Test 2: Cardiology, bookedCount = 12 (slot full)"; start P-1002 12; }

cancel() {
  echo "== Test 3: successful booking followed by cancellation"
  start P-1003 5
  echo "   waiting 15s so the confirmation is sent and the instance sits at 'Wait until appointment'..."
  sleep 15
  api POST /messages/publication "{
    \"name\": \"booking-cancellation\",
    \"correlationKey\": \"P-1003\",
    \"timeToLive\": 60000,
    \"variables\": {\"cancellationReason\": \"Patient request\"}
  }"
}

case "$STEP" in
  deploy)  deploy ;;
  success) success ;;
  full)    full ;;
  cancel)  cancel ;;
  all)     deploy; sleep 2; success; full; cancel ;;
  *) echo "Unknown step: $STEP" >&2; exit 1 ;;
esac
