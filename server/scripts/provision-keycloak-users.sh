#!/usr/bin/env bash
# Provisions users from scripts/seed-output.json into Keycloak using the
# admin partialImport endpoint, which (unlike POST /users) HONORS the
# incoming "id" field. We need that so device_service.devices.user_id values
# (set from the same seed) keep matching the JWT "sub" claim.
#
# Requirements: jq, curl
# Env:    SEED_FILE, KC_URL, REALM, KC_ADMIN_USER, KC_ADMIN_PASS, SEED_PASSWORD

set -euo pipefail

SEED_FILE="${SEED_FILE:-scripts/seed-output.json}"
KC_URL="${KC_URL:-http://localhost:8080}"
REALM="${REALM:-home-energy-tracker-security-realm}"
KC_ADMIN_USER="${KC_ADMIN_USER:-adminp}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:?set KC_ADMIN_PASS}"
SEED_PASSWORD="${SEED_PASSWORD:-password}"

echo "[provision] fetching master admin token"
MASTER_TOKEN=$(curl -s -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=admin-cli" \
  -d "username=$KC_ADMIN_USER" -d "password=$KC_ADMIN_PASS" | jq -r .access_token)
if [ "$MASTER_TOKEN" = "null" ] || [ -z "$MASTER_TOKEN" ]; then
  echo "[provision] FAILED to get master token; check KC_ADMIN_USER / KC_ADMIN_PASS"; exit 1
fi

PAYLOAD=$(jq --arg pw "$SEED_PASSWORD" '{
  ifResourceExists: "SKIP",
  users: [.users[] | {
    id,
    username: (.email | split("@")[0]),
    email,
    firstName,
    lastName,
    enabled: true,
    emailVerified: true,
    requiredActions: [],
    credentials: [{type:"password", value:$pw, temporary:false}],
    realmRoles: ["USER"]
  }]
}' "$SEED_FILE")

echo "[provision] POST $KC_URL/admin/realms/$REALM/partialImport"
RESP=$(curl -s -X POST "$KC_URL/admin/realms/$REALM/partialImport" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

echo "$RESP" | jq '{added, skipped, overwritten}'
echo "[provision] done. login with username (local-part of email) and password: $SEED_PASSWORD"
