import { Injectable, computed, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { appSettings } from './config';

/** Wraps keycloak-js. Login uses the Authorization Code flow with PKCE. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = new Keycloak({ ...appSettings.keycloak });

  private readonly _authenticated = signal(false);
  private readonly _roles = signal<string[]>([]);
  private readonly _userId = signal<string | null>(null);
  private readonly _name = signal('');
  private readonly _email = signal('');

  readonly authenticated = this._authenticated.asReadonly();
  readonly roles = this._roles.asReadonly();
  /** JWT "sub". The backend compares it to the user id in user-service. */
  readonly userId = this._userId.asReadonly();
  readonly name = this._name.asReadonly();
  readonly email = this._email.asReadonly();
  readonly isAdmin = computed(() => this._roles().includes('ADMIN'));
  readonly canIngest = computed(() => this._roles().some((r) => r === 'ADMIN' || r === 'INGESTION'));

  async init(): Promise<void> {
    const ok = await this.keycloak.init({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    });
    if (ok) this.syncFromToken();
    this.keycloak.onAuthRefreshSuccess = () => this.syncFromToken();
    this.keycloak.onTokenExpired = () => {
      this.keycloak.updateToken(30).catch(() => this.login());
    };
  }

  /** Returns a valid access token, refreshing it if it expires within 30s. */
  async getToken(): Promise<string | undefined> {
    if (!this.keycloak.authenticated) return undefined;
    try {
      await this.keycloak.updateToken(30);
    } catch {
      await this.login();
      return undefined;
    }
    return this.keycloak.token;
  }

  login(): Promise<void> {
    return this.keycloak.login();
  }

  logout(): Promise<void> {
    return this.keycloak.logout({ redirectUri: window.location.origin });
  }

  accountUrl(): string {
    return this.keycloak.createAccountUrl();
  }

  private syncFromToken(): void {
    const t = this.keycloak.tokenParsed as Record<string, any> | undefined;
    this._authenticated.set(!!this.keycloak.authenticated);
    this._roles.set(t?.['realm_access']?.['roles'] ?? []);
    this._userId.set(t?.['sub'] ?? null);
    this._name.set(t?.['name'] ?? t?.['preferred_username'] ?? '');
    this._email.set(t?.['email'] ?? '');
  }
}
