import { Routes } from '@angular/router';
import { adminGuard, ingestGuard } from './core/admin.guard';

export const routes: Routes = [
  {
    path: '',
    title: 'Dashboard · Home Energy',
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'devices',
    title: 'Devices · Home Energy',
    loadComponent: () => import('./features/devices/device-list').then((m) => m.DeviceList),
  },
  {
    path: 'devices/new',
    title: 'Add device · Home Energy',
    loadComponent: () => import('./features/devices/device-form').then((m) => m.DeviceForm),
  },
  {
    path: 'devices/:id',
    title: 'Edit device · Home Energy',
    loadComponent: () => import('./features/devices/device-form').then((m) => m.DeviceForm),
  },
  {
    path: 'profile',
    title: 'Profile · Home Energy',
    loadComponent: () => import('./features/profile/profile').then((m) => m.Profile),
  },
  {
    path: 'users',
    title: 'Users · Home Energy',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/users/user-list').then((m) => m.UserList),
  },
  {
    path: 'ingestion',
    title: 'Send reading · Home Energy',
    canActivate: [ingestGuard],
    loadComponent: () => import('./features/ingestion/ingestion').then((m) => m.Ingestion),
  },
  { path: '**', redirectTo: '' },
];
