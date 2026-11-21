import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';
import { UserApi } from '../../core/user-api';
import { User } from '../../core/models';
import { ReadableError, toReadableError } from '../../core/errors';
import { ErrorBanner } from '../../shared/error-banner';

const PHONE = /^[+0-9\-\s()]{7,30}$/;

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, DatePipe, ErrorBanner],
  template: `
    <header class="page-head">
      <div>
        <h1>Profile</h1>
        <p class="muted">Contact details and usage alert settings.</p>
      </div>
      <a class="btn ghost" [href]="auth.accountUrl()" target="_blank" rel="noopener">Change password</a>
    </header>

    <app-error-banner [error]="error()" />
    @if (saved()) {
      <div class="banner success" role="status">Profile saved.</div>
    }

    @if (loading()) {
      <p class="muted">Loading…</p>
    } @else if (notFound()) {
      <div class="banner info">
        user-service has no profile for your account id <code>{{ auth.userId() }}</code>.
        An admin must create one with this id (the Keycloak <code>sub</code>), for example by running
        <code>server/scripts/seed.py</code> and then <code>provision-keycloak-users.sh</code>.
      </div>
    } @else {
      <form class="card form" [formGroup]="form" (ngSubmit)="save()">
        <div class="grid-2">
          <label>
            <span>First name</span>
            <input formControlName="firstName" maxlength="100" />
          </label>
          <label>
            <span>Last name</span>
            <input formControlName="lastName" maxlength="100" />
          </label>
        </div>
        <label>
          <span>Email</span>
          <input type="email" formControlName="email" maxlength="255" />
          @if (form.controls.email.touched && form.controls.email.invalid) {
            <small class="field-error">Enter a valid email.</small>
          }
        </label>
        <label>
          <span>Phone</span>
          <input formControlName="phone" placeholder="+1 555 0100" />
          @if (form.controls.phone.touched && form.controls.phone.invalid) {
            <small class="field-error">7–30 characters: digits, +, -, spaces, ().</small>
          }
        </label>

        <fieldset>
          <legend>Usage alerts</legend>
          <label class="check">
            <input type="checkbox" formControlName="alerting" />
            <span>Email me when my usage goes over my threshold</span>
          </label>
          <label>
            <span>Alert threshold (kWh)</span>
            <input type="number" min="0" step="0.1" formControlName="energyAlertThreshold" />
            @if (form.controls.energyAlertThreshold.invalid) {
              <small class="field-error">Must be 0 or more.</small>
            }
          </label>
        </fieldset>

        @if (user(); as u) {
          <p class="muted small">Account created {{ u.createdAt | date: 'medium' }} · updated {{ u.updatedAt | date: 'medium' }}</p>
        }

        <div class="form-actions">
          <button class="btn" type="submit" [disabled]="form.invalid || form.pristine || saving()">
            {{ saving() ? 'Saving…' : 'Save' }}
          </button>
        </div>
      </form>
    }
  `,
})
export class Profile {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(UserApi);

  protected readonly user = signal<User | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly notFound = signal(false);
  protected readonly error = signal<ReadableError | null>(null);

  protected readonly form = inject(FormBuilder).group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.pattern(PHONE)],
    alerting: [false],
    energyAlertThreshold: [null as number | null, Validators.min(0)],
  });

  constructor() {
    const id = this.auth.userId();
    if (!id) {
      this.loading.set(false);
      return;
    }
    this.api.get(id).subscribe({
      next: (u) => this.apply(u),
      error: (e) => {
        if (e instanceof HttpErrorResponse && e.status === 404) this.notFound.set(true);
        else this.error.set(toReadableError(e));
        this.loading.set(false);
      },
    });
  }

  protected save() {
    const u = this.user();
    if (!u || this.form.invalid) return;
    this.saving.set(true);
    this.saved.set(false);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.api
      .update(u.id, {
        firstName: v.firstName ?? undefined,
        lastName: v.lastName ?? undefined,
        email: v.email ?? undefined,
        phone: v.phone || undefined,
        alerting: !!v.alerting,
        energyAlertThreshold: v.energyAlertThreshold,
      })
      .subscribe({
        next: (updated) => {
          this.apply(updated);
          this.saving.set(false);
          this.saved.set(true);
        },
        error: (e) => {
          this.error.set(toReadableError(e));
          this.saving.set(false);
        },
      });
  }

  private apply(u: User) {
    this.user.set(u);
    this.form.reset({
      firstName: u.firstName,
      lastName: u.lastName,
      email: u.email,
      phone: u.phone ?? '',
      alerting: u.alerting,
      energyAlertThreshold: u.energyAlertThreshold,
    });
    this.loading.set(false);
  }
}
