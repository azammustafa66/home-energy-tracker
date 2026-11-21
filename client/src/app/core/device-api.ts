import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { appSettings } from './config';
import { ApiResponse, CreateDeviceRequest, Device, UpdateDeviceRequest } from './models';

@Injectable({ providedIn: 'root' })
export class DeviceApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${appSettings.apiBase}/devices`;

  /** Without userId: ADMIN only. With userId: ADMIN, or the owner. */
  list(userId?: string | null) {
    const params = userId ? new HttpParams().set('userId', userId) : undefined;
    return this.http.get<ApiResponse<Device[]>>(this.base, { params }).pipe(map((r) => r.data ?? []));
  }

  get(id: string) {
    return this.http.get<ApiResponse<Device>>(`${this.base}/${id}`).pipe(map((r) => r.data!));
  }

  create(body: CreateDeviceRequest) {
    return this.http.post<ApiResponse<Device>>(this.base, body).pipe(map((r) => r.data!));
  }

  update(id: string, body: UpdateDeviceRequest) {
    return this.http.patch<ApiResponse<Device>>(`${this.base}/${id}`, body).pipe(map((r) => r.data!));
  }

  /** ADMIN only */
  delete(id: string) {
    return this.http.delete<ApiResponse<void>>(`${this.base}/${id}`);
  }
}
