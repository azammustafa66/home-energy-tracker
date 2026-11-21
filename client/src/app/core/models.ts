// Mirrors the Spring DTOs in server/common-api, user-service and device-service.

export interface ApiError {
  status: number;
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ApiError;
  timestamp: string;
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  alerting: boolean;
  energyAlertThreshold: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  phone?: string | null;
  alerting?: boolean;
  energyAlertThreshold?: number | null;
}

export type UpdateUserRequest = Partial<CreateUserRequest>;

export const DEVICE_TYPES = [
  'METER',
  'SOLAR_PANEL',
  'BATTERY',
  'APPLIANCE',
  'THERMOSTAT',
  'LIGHT',
  'CCTV',
] as const;

export type DeviceType = (typeof DEVICE_TYPES)[number];

export interface Device {
  id: string;
  name: string;
  type: DeviceType;
  serialNumber: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDeviceRequest {
  name: string;
  type: DeviceType;
  serialNumber: string;
  userId: string;
}

export type UpdateDeviceRequest = Partial<Omit<CreateDeviceRequest, 'userId'>>;

export interface EnergyUsageReading {
  deviceId: string;
  energyConsumed: number;
  timestamp: string;
}
