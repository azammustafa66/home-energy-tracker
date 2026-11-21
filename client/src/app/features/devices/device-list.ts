import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { DeviceApi } from '../../core/device-api';
import { Device, DEVICE_TYPES } from '../../core/models';
import { ReadableError, toReadableError } from '../../core/errors';
import { ErrorBanner } from '../../shared/error-banner';

@Component({
  selector: 'app-device-list',
  imports: [RouterLink, DatePipe, FormsModule, ErrorBanner],
  template: `
    <header class="page-head">
      <div>
        <h1>Devices</h1>
        <p class="muted">{{ auth.isAdmin() ? 'Every registered device.' : 'Devices registered to you.' }}</p>
      </div>
      <a class="btn" routerLink="/devices/new">Add device</a>
    </header>

    <app-error-banner [error]="error()" />

    <div class="toolbar">
      <input type="search" placeholder="Search name or serial…" [(ngModel)]="query" aria-label="Search devices" />
      <select [(ngModel)]="typeFilter" aria-label="Filter by type">
        <option value="">All types</option>
        @for (t of types; track t) {
          <option [value]="t">{{ t }}</option>
        }
      </select>
      @if (auth.isAdmin()) {
        <input placeholder="Filter by user id" [(ngModel)]="userFilter" (change)="load()" aria-label="Filter by user id" />
      }
    </div>

    @if (loading()) {
      <p class="muted">Loading…</p>
    } @else if (filtered().length === 0) {
      <div class="card empty">
        <p>No devices found.</p>
        <a class="btn" routerLink="/devices/new">Add device</a>
      </div>
    } @else {
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Type</th>
              <th>Serial</th>
              @if (auth.isAdmin()) {
                <th>Owner</th>
              }
              <th>Updated</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (d of filtered(); track d.id) {
              <tr>
                <td><a [routerLink]="['/devices', d.id]">{{ d.name }}</a></td>
                <td><span class="chip">{{ d.type }}</span></td>
                <td><code>{{ d.serialNumber }}</code></td>
                @if (auth.isAdmin()) {
                  <td><code class="truncate" [title]="d.userId">{{ d.userId }}</code></td>
                }
                <td class="muted">{{ d.updatedAt | date: 'medium' }}</td>
                <td class="actions">
                  <a class="btn ghost small" [routerLink]="['/devices', d.id]">Edit</a>
                  @if (auth.isAdmin()) {
                    <button class="btn danger small" type="button" (click)="remove(d)" [disabled]="busyId() === d.id">
                      Delete
                    </button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class DeviceList {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(DeviceApi);

  protected readonly types = DEVICE_TYPES;
  protected readonly devices = signal<Device[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<ReadableError | null>(null);
  protected readonly busyId = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly typeFilter = signal('');
  protected readonly userFilter = signal('');

  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const t = this.typeFilter();
    return this.devices().filter(
      (d) =>
        (!t || d.type === t) &&
        (!q || d.name.toLowerCase().includes(q) || d.serialNumber.toLowerCase().includes(q)),
    );
  });

  constructor() {
    this.load();
  }

  protected load() {
    this.loading.set(true);
    this.error.set(null);
    const userId = this.auth.isAdmin() ? this.userFilter().trim() || null : this.auth.userId();
    this.api.list(userId).subscribe({
      next: (list) => {
        this.devices.set(list);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(toReadableError(e));
        this.loading.set(false);
      },
    });
  }

  protected remove(d: Device) {
    if (!confirm(`Delete "${d.name}"? This cannot be undone.`)) return;
    this.busyId.set(d.id);
    this.api.delete(d.id).subscribe({
      next: () => {
        this.devices.update((list) => list.filter((x) => x.id !== d.id));
        this.busyId.set(null);
      },
      error: (e) => {
        this.error.set(toReadableError(e));
        this.busyId.set(null);
      },
    });
  }
}
