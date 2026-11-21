import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { DeviceApi } from '../../core/device-api';
import { UserApi } from '../../core/user-api';
import { DEVICE_TYPES, DeviceType, User } from '../../core/models';
import { ReadableError, toReadableError } from '../../core/errors';
import { ErrorBanner } from '../../shared/error-banner';

@Component({
  selector: 'app-device-form',
  imports: [ReactiveFormsModule, RouterLink, ErrorBanner],
  template: `
    <header class="page-head">
      <div>
        <a class="back" routerLink="/devices">← Devices</a>
        <h1>{{ id() ? 'Edit device' : 'Add device' }}</h1>
      </div>
    </header>

    <app-error-banner [error]="error()" />

    @if (loading()) {
      <p class="muted">Loading…</p>
    } @else {
      <form class="card form" [formGroup]="form" (ngSubmit)="save()">
        <label>
          <span>Name</span>
          <input formControlName="name" maxlength="150" placeholder="Kitchen fridge" />
          @if (form.controls.name.touched && form.controls.name.invalid) {
            <small class="field-error">Name is required.</small>
          }
        </label>

        <label>
          <span>Type</span>
          <select formControlName="type">
            @for (t of types; track t) {
              <option [value]="t">{{ t }}</option>
            }
          </select>
        </label>

        <label>
          <span>Serial number</span>
          <input formControlName="serialNumber" maxlength="100" placeholder="SN-0001" />
          @if (form.controls.serialNumber.touched && form.controls.serialNumber.invalid) {
            <small class="field-error">Serial number is required.</small>
          }
        </label>

        @if (!id() && auth.isAdmin()) {
          <label>
            <span>Owner</span>
            <select formControlName="userId">
              @for (u of users(); track u.id) {
                <option [value]="u.id">{{ u.firstName }} {{ u.lastName }} · {{ u.email }}</option>
              }
            </select>
            <small class="muted">Defaults to you. Admins can register devices for any user.</small>
          </label>
        }

        <div class="form-actions">
          <a class="btn ghost" routerLink="/devices">Cancel</a>
          <button class="btn" type="submit" [disabled]="form.invalid || saving()">
            {{ saving() ? 'Saving…' : id() ? 'Save changes' : 'Add device' }}
          </button>
        </div>
      </form>
    }
  `,
})
export class DeviceForm implements OnInit {
  /** Route param, bound by withComponentInputBinding(). Missing on /devices/new. */
  readonly id = input<string>();

  protected readonly auth = inject(AuthService);
  private readonly api = inject(DeviceApi);
  private readonly usersApi = inject(UserApi);
  private readonly router = inject(Router);

  protected readonly types = DEVICE_TYPES;
  protected readonly users = signal<User[]>([]);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<ReadableError | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    type: ['METER' as DeviceType, Validators.required],
    serialNumber: ['', [Validators.required, Validators.maxLength(100)]],
    userId: [''],
  });

  ngOnInit() {
    const id = this.id();
    this.form.controls.userId.setValue(this.auth.userId() ?? '');
    if (!id && this.auth.isAdmin()) {
      this.usersApi.list().subscribe({ next: (u) => this.users.set(u), error: () => {} });
    }
    if (id) {
      this.loading.set(true);
      this.api.get(id).subscribe({
        next: (d) => {
          this.form.patchValue({ name: d.name, type: d.type, serialNumber: d.serialNumber, userId: d.userId });
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(toReadableError(e));
          this.loading.set(false);
        },
      });
    }
  }

  protected save() {
    if (this.form.invalid) return;
    this.saving.set(true);
    this.error.set(null);
    const { name, type, serialNumber, userId } = this.form.getRawValue();
    const id = this.id();
    const req$ = id
      ? this.api.update(id, { name, type, serialNumber })
      : this.api.create({ name, type, serialNumber, userId });
    req$.subscribe({
      next: () => this.router.navigate(['/devices']),
      error: (e) => {
        this.error.set(toReadableError(e));
        this.saving.set(false);
      },
    });
  }
}
