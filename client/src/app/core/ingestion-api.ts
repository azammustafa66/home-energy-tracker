import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { appSettings } from './config';
import { EnergyUsageReading } from './models';

@Injectable({ providedIn: 'root' })
export class IngestionApi {
  private readonly http = inject(HttpClient);

  /** ADMIN or INGESTION role. Returns 201 with no body; the reading goes to Kafka topic "energy-usage". */
  send(reading: EnergyUsageReading) {
    return this.http.post<void>(`${appSettings.apiBase}/ingestion`, reading);
  }
}
