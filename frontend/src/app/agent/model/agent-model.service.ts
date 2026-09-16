import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { AgentModelOption } from '../agent-ui.types';

@Injectable({ providedIn: 'root' })
export class AgentModelService {
  private readonly http = inject(HttpClient);

  readonly options = signal<readonly AgentModelOption[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.http.get<AgentModelOption[]>('/api/agent-model-options').subscribe({
      next: options => {
        this.options.set(options);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить модели агента.');
        this.loading.set(false);
      },
    });
  }
}
