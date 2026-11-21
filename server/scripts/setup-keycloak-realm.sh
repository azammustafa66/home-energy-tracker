#!/usr/bin/env bash
# Creates the Keycloak realm the services expect, on a fresh Keycloak:
#   - realm  home-energy-tracker-security-realm
#   - realm roles USER (default for new users), ADMIN, INGESTION
#   - confidential client "energy-sensors" (client_credentials, has INGESTION;
#     secret = ENERGY_SENSORS_CLIENT_SECRET) used by the ingestion simulator
#   - public client "het-web" (auth code + PKCE) used by the Angular app
# Safe to re-run: anything that already exists is left alone.
#
# Requirements: curl, jq. Run from server/:  ./scripts/setup-keycloak-realm.sh
# Reads KEYCLOAK_ADMIN, KEYCLOAK_PASSWORD, ENERGY_SENSORS_CLIENT_SECRET from .env

set -euo pipefail

[ -f .env ] && { set -a; . ./.env; set +a; }

KC_URL="${KC_URL:-http://localhost:8080}"
REALM="${REALM:-home-energy-tracker-security-realm}"
WEB_URL="${WEB_URL:-http://localhost:4200}"
: "${KEYCLOAK_ADMIN:?KEYCLOAK_ADMIN not set}"
: "${KEYCLOAK_PASSWORD:?KEYCLOAK_PASSWORD not set}"
: "${ENERGY_SENSORS_CLIENT_SECRET:?ENERGY_SENSORS_CLIENT_SECRET not set}"

TOKEN=$(curl -s -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  --data-urlencode "username=$KEYCLOAK_ADMIN" --data-urlencode "password=$KEYCLOAK_PASSWORD" | jq -r .access_token)
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "Could not get a master admin token"; exit 1; }

api() { # api METHOD PATH [JSON]
  local m=$1 p=$2; shift 2
  curl -s -X "$m" "$KC_URL/admin$p" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" "$@"
}

# Realm
if [ "$(api GET "/realms/$REALM" -o /dev/null -w '%{http_code}')" = 404 ]; then
  api POST /realms -d "{\"realm\":\"$REALM\",\"enabled\":true}" -o /dev/null
  echo "created realm $REALM"
fi

# Roles
for role in USER ADMIN INGESTION; do
  if [ "$(api GET "/realms/$REALM/roles/$role" -o /dev/null -w '%{http_code}')" = 404 ]; then
    api POST "/realms/$REALM/roles" -d "{\"name\":\"$role\"}" -o /dev/null
    echo "created role $role"
  fi
done

# Every new user gets USER
USER_ROLE=$(api GET "/realms/$REALM/roles/USER")
api POST "/realms/$REALM/roles/default-roles-${REALM}/composites" -d "[$USER_ROLE]" -o /dev/null

client_uuid() { api GET "/realms/$REALM/clients?clientId=$1" | jq -r '.[0].id // empty'; }

# energy-sensors: machine client for the ingestion simulator
if [ -z "$(client_uuid energy-sensors)" ]; then
  api POST "/realms/$REALM/clients" -o /dev/null -d "$(jq -n --arg s "$ENERGY_SENSORS_CLIENT_SECRET" '{
    clientId: "energy-sensors", enabled: true, publicClient: false, secret: $s,
    serviceAccountsEnabled: true, standardFlowEnabled: false, directAccessGrantsEnabled: false
  }')"
  CID=$(client_uuid energy-sensors)
  SA=$(api GET "/realms/$REALM/clients/$CID/service-account-user" | jq -r .id)
  api POST "/realms/$REALM/users/$SA/role-mappings/realm" -d "[$(api GET "/realms/$REALM/roles/INGESTION")]" -o /dev/null
  echo "created client energy-sensors (role INGESTION)"
fi

# het-web: browser client for the Angular app
if [ -z "$(client_uuid het-web)" ]; then
  api POST "/realms/$REALM/clients" -o /dev/null -d "$(jq -n --arg url "$WEB_URL" '{
    clientId: "het-web", name: "Home Energy Tracker web", enabled: true, publicClient: true,
    standardFlowEnabled: true, directAccessGrantsEnabled: false,
    rootUrl: $url, baseUrl: "/", redirectUris: [($url + "/*")], webOrigins: [$url],
    attributes: { "pkce.code.challenge.method": "S256", "post.logout.redirect.uris": ($url + "/*") }
  }')"
  echo "created client het-web ($WEB_URL)"
fi

# admin-cli (password grant, handy for scripts/curl): Keycloak 26 gives it
# "lightweight" access tokens with no sub and no roles, which the services reject.
CID=$(client_uuid admin-cli)
api GET "/realms/$REALM/clients/$CID" \
  | jq '.attributes["client.use.lightweight.access.token.enabled"]="false"' \
  | api PUT "/realms/$REALM/clients/$CID" -d @- -o /dev/null

echo "realm $REALM ready"
