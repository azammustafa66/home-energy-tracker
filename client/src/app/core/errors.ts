import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from './models';

export interface ReadableError {
  message: string;
  fieldErrors?: Record<string, string>;
}

/** Turns a failed request into a message for the UI. Handles the APIResponse envelope and gateway fallbacks. */
export function toReadableError(err: unknown): ReadableError {
  if (!(err instanceof HttpErrorResponse)) return { message: String(err) };
  const body = err.error as ApiResponse<unknown> | string | null;
  if (body && typeof body === 'object' && body.error) {
    return { message: body.error.message, fieldErrors: body.error.fieldErrors };
  }
  if (typeof body === 'string' && body.trim()) return { message: body };
  switch (err.status) {
    case 0:
      return { message: 'Cannot reach the API gateway. Is it running on port 8000?' };
    case 401:
      return { message: 'Your session expired. Sign in again.' };
    case 403:
      return { message: 'You do not have permission to do that.' };
    case 404:
      return { message: 'Not found.' };
    case 503:
      return { message: 'The service is unavailable right now (circuit breaker open). Try again shortly.' };
    default:
      return { message: `Request failed (${err.status} ${err.statusText}).` };
  }
}
