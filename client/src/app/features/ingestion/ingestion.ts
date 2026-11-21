import { Component, OnDestroy, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { DeviceApi } from '../../core/device-api';
import { IngestionApi } from '../../core/ingestion-api';
import { Device } from '../../core/models';
import { ReadableError, toReadableError } from '../../core/errors';
import { ErrorBanner } from '../../shared/error-banner';

interface SentReading {
  deviceName: string;
  energyConsumed: number;
  timestamp: string;
  ok: boolean;
}

@Component({
  selector: 'app-ingestion',
  imports: [ReactiveFormsModule, DatePipe, DecimalPipe, ErrorBanner],
  template: `
    <header class="page-head">
      <div>
        <h1>Send reading</h1>
        <p class="muted">
          Posts to <code>/api/v1/ingestion</code>. The reading goes to Kafka, then usage-service, then InfluxDB.
          Needs the ADMIN or INGESTION role.
        </p>
      </div>
    </header>

    <app-error-banner [error]="error()" />

    <form class="card form" [formGroup]="form" (ngSubmit)="sendOnce()">
      <label>
        <span>Device</span>
        <select formControlName="deviceId">
          @for (d of devices(); track d.id) {
            <option [value]="d.id">{{ d.name }} · {{ d.type }}</option>
          } @empty {
            <option value="" disabled>No devices. Add one first.</option>
          }
        </select>
      </label>
      <div class="grid-2">
        <label>
          <span>Energy consumed (kWh)</span>
          <input type="number" min="0" step="0.01" formControlName="energyConsumed" />
        </label>
        <label>
          <span>Auto-send every (seconds)</span>
          <input type="number" min="1" formControlName="intervalSec" />
        </label>
      </div>
      <div class="form-actions">
        <button class="btn ghost" type="button" (click)="toggleAuto()" [disabled]="form.invalid">
          {{ auto() ? 'Stop auto-send' : 'Start auto-send (random values)' }}
        </button>
        <button class="btn" type="submit" [disabled]="form.invalid || sending()">Send once</button>
      </div>
    </form>

    <section class="card">
      <h2>Sent this session</h2>
      @if (sent().length === 0) {
        <p class="muted">Nothing sent yet.</p>
      } @else {
        <table>
          <thead><tr><th>Time</th><th>Device</th><th>kWh</th><th>Status</th></tr></thead>
          <tbody>
            @for (r of sent(); track $index) {
              <tr>
                <td class="muted">{{ r.timestamp | date: 'mediumTime' }}</td>
                <td>{{ r.deviceName }}</td>
                <td>{{ r.energyConsumed | number: '1.2-2' }}</td>
                <td><span class="chip" [class.on]="r.ok" [class.bad]="!r.ok">{{ r.ok ? 'Accepted' : 'Failed' }}</span></td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
})
export class Ingestion implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly devicesApi = inject(DeviceApi);
  private readonly api = inject(IngestionApi);

  protected readonly devices = signal<Device[]>([]);
  protected readonly sent = signal<SentReading[]>([]);
  protected readonly sending = signal(false);
  protected readonly error = signal<ReadableError | null>(null);
  protected readonly auto = signal(false);
  private timer: ReturnType<typeof setInterval> | null = null;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    deviceId: ['', Validators.required],
    energyConsumed: [1.5, [Validators.required, Validators.min(0)]],
    intervalSec: [2, [Validators.required, Validators.min(1)]],
  });

  constructor() {
    this.devicesApi.list(this.auth.isAdmin() ? null : this.auth.userId()).subscribe({
      next: (d) => {
        this.devices.set(d);
        if (d.length) this.form.controls.deviceId.setValue(d[0].id);
      },
      error: (e) => this.error.set(toReadableError(e)),
    });
  }

  ngOnDestroy() {
    this.stop();
  }

  protected sendOnce() {
    this.send(this.form.controls.energyConsumed.value);
  }

  protected toggleAuto() {
    if (this.timer) return this.stop();
    const ms = this.form.controls.intervalSec.value * 1000;
    const max = Math.max(this.form.controls.energyConsumed.value, 0.1) * 2;
    this.timer = setInterval(() => this.send(+(Math.random() * max).toFixed(2)), ms);
    this.auto.set(true);
  }

  private stop() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    this.auto.set(false);
  }

  private send(energyConsumed: number) {
    const deviceId = this.form.controls.deviceId.value;
    const device = this.devices().find((d) => d.id === deviceId);
    const timestamp = new Date().toISOString();
    this.sending.set(true);
    this.api.send({ deviceId, energyConsumed, timestamp }).subscribe({
      next: () => this.record({ deviceName: device?.name ?? deviceId, energyConsumed, timestamp, ok: true }),
      error: (e) => {
        this.error.set(toReadableError(e));
        this.record({ deviceName: device?.name ?? deviceId, energyConsumed, timestamp, ok: false });
        this.stop();
      },
    });
  }

  private record(r: SentReading) {
    this.sending.set(false);
    this.sent.update((list) => [r, ...list].slice(0, 50));
  }
}
