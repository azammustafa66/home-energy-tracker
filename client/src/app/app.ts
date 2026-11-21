import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <aside class="sidebar">
        <a class="brand" routerLink="/">
          <span class="brand-mark" aria-hidden="true">⚡</span>
          <span>Home Energy</span>
        </a>
        <nav>
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Dashboard</a>
          <a routerLink="/devices" routerLinkActive="active">Devices</a>
          <a routerLink="/profile" routerLinkActive="active">Profile</a>
          @if (auth.canIngest()) {
            <a routerLink="/ingestion" routerLinkActive="active">Send reading</a>
          }
          @if (auth.isAdmin()) {
            <span class="nav-label">Admin</span>
            <a routerLink="/users" routerLinkActive="active">Users</a>
          }
        </nav>
        <div class="who">
          <div class="who-name">{{ auth.name() || 'Signed in' }}</div>
          <div class="who-meta">{{ auth.email() }}</div>
          <div class="chips">
            @for (role of visibleRoles(); track role) {
              <span class="chip">{{ role }}</span>
            }
          </div>
          <button class="btn ghost small" type="button" (click)="auth.logout()">Sign out</button>
        </div>
      </aside>
      <main class="content">
        <router-outlet />
      </main>
    </div>
  `,
})
export class App {
  protected readonly auth = inject(AuthService);

  protected visibleRoles() {
    return this.auth.roles().filter((r) => ['ADMIN', 'USER', 'INGESTION'].includes(r));
  }
}
