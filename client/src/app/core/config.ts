/**
 * Runtime settings for the frontend.
 * Values match the application.properties files under server/.
 */
export const appSettings = {
  /** API calls go to /api/... and the dev server proxies them to the gateway (port 8000). See proxy.conf.json. */
  apiBase: '/api/v1',
  keycloak: {
    url: 'http://localhost:8080',
    realm: 'home-energy-tracker-security-realm',
    /** Public client for the browser. Create it with scripts/keycloak-web-client.sh */
    clientId: 'het-web',
  },
} as const;
