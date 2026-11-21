import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { appSettings } from './config';
import { ApiResponse, CreateUserRequest, UpdateUserRequest, User } from './models';

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${appSettings.apiBase}/users`;

  /** ADMIN only */
  list() {
    return this.http.get<ApiResponse<User[]>>(this.base).pipe(map((r) => r.data ?? []));
  }

  /** ADMIN, or the user whose id matches the token's sub */
  get(id: string) {
    return this.http.get<ApiResponse<User>>(`${this.base}/${id}`).pipe(map((r) => r.data!));
  }

  /** ADMIN only */
  create(body: CreateUserRequest) {
    return this.http.post<ApiResponse<User>>(this.base, body).pipe(map((r) => r.data!));
  }

  update(id: string, body: UpdateUserRequest) {
    return this.http.patch<ApiResponse<User>>(`${this.base}/${id}`, body).pipe(map((r) => r.data!));
  }

  /** ADMIN only */
  delete(id: string) {
    return this.http.delete<ApiResponse<void>>(`${this.base}/${id}`);
  }
}
