import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of, catchError } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { DeviceApi } from '../../core/device-api';
import { UserApi } from '../../core/user-api';
import { Device, DeviceType, User } from '../../core/models';
import { ReadableError, toReadableError } from '../../core/errors';
import { ErrorBanner } from '../../shared/error-banner';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, ErrorBanner],
  template: `
    <header class="page-head">
      <div>
        <h1>Hello{{ auth.name() ? ', ' + auth.name() : '' }}</h1>
        <p class="muted">Your devices and alert settings at a glance.</p>
      </div>
      <a class="btn" routerLink="/devices/new">Add device</a>
    </header>

    <app-error-banner [error]="error()" />

    @if (loading()) {
      <p class="muted">Loading…</p>
    } @else {
      <section class="stats">
        <div class="stat">
          <div class="stat-label">{{ auth.isAdmin() ? 'All devices' : 'Your devices' }}</div>
          <div class="stat-value">{{ devices().length }}</div>
        </div>
        <div class="stat">
          <div class="stat-label">Device types</div>
          <div class="stat-value">{{ byType().length }}</div>
        </div>
        <div class="stat">
          <div class="stat-label">Usage alerts</div>
          <div class="stat-value">
            @if (me(); as u) {
              {{ u.alerting ? 'On' : 'Off' }}
            } @else {
              —
            }
          </div>
        </div>
        <div class="stat">
          <div class="stat-label">Alert threshold</div>
          <div class="stat-value">
            @if (me()?.energyAlertThreshold != null) {
              {{ me()!.energyAlertThreshold }} <small>kWh</small>
            } @else {
              —
            }
          </div>
        </div>
        @if (auth.isAdmin()) {
          <div class="stat">
            <div class="stat-label">Users</div>
            <div class="stat-value">{{ userCount() ?? '—' }}</div>
          </div>
        }
      </section>

      @if (!me() && meMissing()) {
        <div class="banner info">
          No profile found in user-service for your account id
          <code>{{ auth.userId() }}</code>. Alerts need one. An admin can create it, or seed users with
          <code>server/scripts/seed.py</code>.
        </div>
      }

      <section class="card">
        <h2>Devices by type</h2>
        @if (byType().length === 0) {
          <p class="muted">No devices yet. <a routerLink="/devices/new">Add your first device.</a></p>
        } @else {
          <ul class="bars">
            @for (row of byType(); track row.type) {
              <li>
                <span class="bar-label">{{ label(row.type) }}</span>
                <span class="bar-track"><span class="bar" [style.width.%]="(row.count / maxCount()) * 100"></span></span>
                <span class="bar-value">{{ row.count }}</span>
              </li>
            }
          </ul>
        }
      </section>

      <section class="card">
        <h2>How readings flow</h2>
        <p class="muted">
          Readings go to ingestion-service, then Kafka (<code>energy-usage</code>), then usage-service
          writes them to InfluxDB. When usage goes over your threshold, alert-service emails you. Usage
          history isn't served over REST yet, so charts live in Grafana at
          <a href="http://localhost:3000" target="_blank" rel="noopener">localhost:3000</a>.
        </p>
      </section>
    }
  `,
})
export class Dashboard {
  protected readonly auth = inject(AuthService);
  private readonly devicesApi = inject(DeviceApi);
  private readonly usersApi = inject(UserApi);

  protected readonly loading = signal(true);
  protected readonly error = signal<ReadableError | null>(null);
  protected readonly devices = signal<Device[]>([]);
  protected readonly me = signal<User | null>(null);
  protected readonly meMissing = signal(false);
  protected readonly userCount = signal<number | null>(null);

  protected readonly byType = computed(() => {
    const counts = new Map<DeviceType, number>();
    for (const d of this.devices()) counts.set(d.type, (counts.get(d.type) ?? 0) + 1);
    return [...counts.entries()].map(([type, count]) => ({ type, count })).sort((a, b) => b.count - a.count);
  });
  protected readonly maxCount = computed(() => Math.max(1, ...this.byType().map((r) => r.count)));

  constructor() {
    const id = this.auth.userId();
    const admin = this.auth.isAdmin();
    forkJoin({
      devices: this.devicesApi.list(admin ? null : id),
      me: id
        ? this.usersApi.get(id).pipe(
            catchError(() => {
              this.meMissing.set(true);
              return of(null);
            }),
          )
        : of(null),
      users: admin ? this.usersApi.list().pipe(catchError(() => of(null))) : of(null),
    }).subscribe({
      next: ({ devices, me, users }) => {
        this.devices.set(devices);
        this.me.set(me);
        this.userCount.set(users ? users.length : null);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(toReadableError(e));
        this.loading.set(false);
      },
    });
  }

  protected label(t: string) {
    return t.replace('_', ' ').toLowerCase();
  }
}
