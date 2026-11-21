import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const adminGuard: CanActivateFn = () =>
  inject(AuthService).isAdmin() || inject(Router).createUrlTree(['/']);

export const ingestGuard: CanActivateFn = () =>
  inject(AuthService).canIngest() || inject(Router).createUrlTree(['/']);
