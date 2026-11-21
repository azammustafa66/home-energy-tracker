import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserApi } from '../../core/user-api';
import { User } from '../../core/models';
import { ReadableError, toReadableError } from '../../core/errors';
import { ErrorBanner } from '../../shared/error-banner';

@Component({
  selector: 'app-user-list',
  imports: [DatePipe, FormsModule, ReactiveFormsModule, ErrorBanner],
  template: `
    <header class="page-head">
      <div>
        <h1>Users</h1>
        <p class="muted">Admin only. Profiles in user-service.</p>
      </div>
      <button class="btn" type="button" (click)="showForm.set(!showForm())">
        {{ showForm() ? 'Close' : 'New user' }}
      </button>
    </header>

    <app-error-banner [error]="error()" />

    @if (showForm()) {
      <form class="card form" [formGroup]="form" (ngSubmit)="create()">
        <h2>New user</h2>
        <p class="muted small">
          This only creates the profile. To sign in, the user also needs a Keycloak account whose id matches the new profile id.
        </p>
        <div class="grid-2">
          <label><span>First name</span><input formControlName="firstName" /></label>
          <label><span>Last name</span><input formControlName="lastName" /></label>
        </div>
        <div class="grid-2">
          <label><span>Email</span><input type="email" formControlName="email" /></label>
          <label><span>Phone</span><input formControlName="phone" /></label>
        </div>
        <div class="grid-2">
          <label class="check"><input type="checkbox" formControlName="alerting" /><span>Usage alerts on</span></label>
          <label><span>Alert threshold (kWh)</span><input type="number" min="0" step="0.1" formControlName="energyAlertThreshold" /></label>
        </div>
        <div class="form-actions">
          <button class="btn" type="submit" [disabled]="form.invalid || saving()">{{ saving() ? 'Creating…' : 'Create user' }}</button>
        </div>
      </form>
    }

    <div class="toolbar">
      <input type="search" placeholder="Search name or email…" [(ngModel)]="query" aria-label="Search users" />
    </div>

    @if (loading()) {
      <p class="muted">Loading…</p>
    } @else {
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Alerts</th>
              <th>Threshold</th>
              <th>Created</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (u of filtered(); track u.id) {
              <tr>
                <td>
                  {{ u.firstName }} {{ u.lastName }}
                  <div><code class="truncate small" [title]="u.id">{{ u.id }}</code></div>
                </td>
                <td>{{ u.email }}</td>
                <td><span class="chip" [class.on]="u.alerting">{{ u.alerting ? 'On' : 'Off' }}</span></td>
                <td>{{ u.energyAlertThreshold ?? '—' }}</td>
                <td class="muted">{{ u.createdAt | date: 'mediumDate' }}</td>
                <td class="actions">
                  <button class="btn danger small" type="button" (click)="remove(u)" [disabled]="busyId() === u.id">Delete</button>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="muted">No users found.</td></tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class UserList {
  private readonly api = inject(UserApi);

  protected readonly users = signal<User[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly showForm = signal(false);
  protected readonly busyId = signal<string | null>(null);
  protected readonly error = signal<ReadableError | null>(null);
  protected readonly query = signal('');

  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    return this.users().filter(
      (u) => !q || `${u.firstName} ${u.lastName} ${u.email}`.toLowerCase().includes(q),
    );
  });

  protected readonly form = inject(FormBuilder).group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.pattern(/^[+0-9\-\s()]{7,30}$/)],
    alerting: [true],
    energyAlertThreshold: [null as number | null, Validators.min(0)],
  });

  constructor() {
    this.api.list().subscribe({
      next: (u) => {
        this.users.set(u);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(toReadableError(e));
        this.loading.set(false);
      },
    });
  }

  protected create() {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.api
      .create({
        firstName: v.firstName!,
        lastName: v.lastName!,
        email: v.email!,
        phone: v.phone || null,
        alerting: !!v.alerting,
        energyAlertThreshold: v.energyAlertThreshold,
      })
      .subscribe({
        next: (u) => {
          this.users.update((list) => [u, ...list]);
          this.form.reset({ alerting: true });
          this.showForm.set(false);
          this.saving.set(false);
        },
        error: (e) => {
          this.error.set(toReadableError(e));
          this.saving.set(false);
        },
      });
  }

  protected remove(u: User) {
    if (!confirm(`Delete ${u.email}? Their devices are not deleted.`)) return;
    this.busyId.set(u.id);
    this.api.delete(u.id).subscribe({
      next: () => {
        this.users.update((list) => list.filter((x) => x.id !== u.id));
        this.busyId.set(null);
      },
      error: (e) => {
        this.error.set(toReadableError(e));
        this.busyId.set(null);
      },
    });
  }
}
