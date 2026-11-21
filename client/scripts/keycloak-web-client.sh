#!/usr/bin/env bash
# Creates the public "het-web" client in Keycloak so the Angular app can log in
# (Authorization Code + PKCE). Safe to re-run: it skips the client if it exists.
#
# Requirements: curl, jq. Keycloak must be running (docker compose up -d in ../server).
# Env: KC_URL, REALM, KC_ADMIN_USER, KC_ADMIN_PASS (see server/.env KEYCLOAK_ADMIN / KEYCLOAK_PASSWORD), APP_URL

set -euo pipefail

KC_URL="${KC_URL:-http://localhost:8080}"
REALM="${REALM:-home-energy-tracker-security-realm}"
KC_ADMIN_USER="${KC_ADMIN_USER:-admin}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:?set KC_ADMIN_PASS to the Keycloak admin password}"
APP_URL="${APP_URL:-http://localhost:4200}"
CLIENT_ID="het-web"

TOKEN=$(curl -s -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  -d "username=$KC_ADMIN_USER" -d "password=$KC_ADMIN_PASS" | jq -r .access_token)
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "Could not get an admin token. Check KC_ADMIN_USER / KC_ADMIN_PASS."; exit 1; }

EXISTING=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "$KC_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" | jq length)
if [ "$EXISTING" != "0" ]; then
  echo "Client $CLIENT_ID already exists in realm $REALM. Nothing to do."
  exit 0
fi

curl -sf -X POST "$KC_URL/admin/realms/$REALM/clients" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "$(jq -n --arg id "$CLIENT_ID" --arg url "$APP_URL" '{
    clientId: $id,
    name: "Home Energy Tracker web",
    enabled: true,
    publicClient: true,
    protocol: "openid-connect",
    standardFlowEnabled: true,
    directAccessGrantsEnabled: false,
    implicitFlowEnabled: false,
    serviceAccountsEnabled: false,
    rootUrl: $url,
    baseUrl: "/",
    redirectUris: [($url + "/*")],
    webOrigins: [$url],
    attributes: {
      "pkce.code.challenge.method": "S256",
      "post.logout.redirect.uris": ($url + "/*")
    }
  }')"

echo "Created public client $CLIENT_ID (redirect $APP_URL/*, PKCE S256) in realm $REALM."
