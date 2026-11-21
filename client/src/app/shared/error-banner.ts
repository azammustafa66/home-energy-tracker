import { Component, input } from '@angular/core';
import { ReadableError } from '../core/errors';

@Component({
  selector: 'app-error-banner',
  template: `
    @if (error(); as e) {
      <div class="banner error" role="alert">
        <strong>{{ e.message }}</strong>
        @if (e.fieldErrors) {
          <ul>
            @for (f of fields(e.fieldErrors); track f[0]) {
              <li><code>{{ f[0] }}</code>: {{ f[1] }}</li>
            }
          </ul>
        }
      </div>
    }
  `,
})
export class ErrorBanner {
  readonly error = input<ReadableError | null>(null);
  protected fields(map: Record<string, string>) {
    return Object.entries(map);
  }
}
