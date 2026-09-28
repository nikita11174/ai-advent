import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, inject, OnDestroy, OnInit, Output, signal } from '@angular/core';
import { MonitorCommand, MonitorView, RepositoryMonitorService } from './repository-monitor.service';

@Component({
  selector: 'app-repository-monitor',
  imports: [DatePipe],
  templateUrl: './repository-monitor.html',
  styleUrl: './repository-monitor.scss',
})
export class RepositoryMonitor implements OnInit, OnDestroy {
  @Output() readonly explain = new EventEmitter<void>();
  private readonly api = inject(RepositoryMonitorService);
  private poller?: ReturnType<typeof setInterval>;
  private readInFlight = false;
  private uncertain: MonitorCommand | null = null;
  readonly view = signal<MonitorView | null>(null);
  readonly minimumInterval = signal(60);
  readonly interval = signal(300);
  readonly busy = signal(false);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly unresolved = signal(false);

  ngOnInit(): void {
    this.api.configuration().subscribe({
      next: config => {
        this.minimumInterval.set(config.minimumIntervalSeconds);
        this.interval.set(config.minimumIntervalSeconds === 10 ? 10 : config.defaultIntervalSeconds);
      },
      error: () => this.error.set('Не удалось получить допустимый интервал монитора.'),
    });
    this.refresh();
    this.poller = setInterval(() => this.refresh(), 4000);
  }

  ngOnDestroy(): void { clearInterval(this.poller); }

  refresh(): void {
    if (this.readInFlight || this.busy()) return;
    this.readInFlight = true;
    this.api.current().subscribe({
      next: view => { this.acceptView(view); this.loading.set(false); this.readInFlight = false; },
      error: (response: HttpErrorResponse) => {
        this.error.set(this.errorText(response)); this.loading.set(false); this.readInFlight = false;
      },
    });
  }

  start(): void { this.command('START'); }
  stop(): void { this.command('STOP'); }

  healthLabel(status: MonitorView['health']['status']): string {
    return { IDLE: 'Остановлен', WAITING: 'Ожидание следующей проверки',
      RUNNING: 'Проверка выполняется', FAULTED: 'Ошибка монитора' }[status];
  }

  private command(action: 'START' | 'STOP'): void {
    const view = this.view();
    if (!view || this.busy() || this.unresolved() || (action === 'START' ? view.enabled : !view.enabled)
        || (action === 'START' && this.minimumInterval() > this.interval())) return;
    const command: MonitorCommand = { commandId: crypto.randomUUID(), expectedRevision: view.configRevision,
      ...(action === 'START' ? { intervalSeconds: this.interval() } : {}) };
    this.busy.set(true); this.error.set(''); this.notice.set('');
    const request = action === 'START' ? this.api.start(command) : this.api.stop(command);
    request.subscribe({
      next: result => {
        if (result.operationStatus === 'UNKNOWN' || !result.view) { this.reconcile(command); return; }
        this.acceptView(result.view);
        this.notice.set(action === 'START' ? 'Монитор запущен.' : 'Монитор остановлен.');
        this.busy.set(false);
      },
      error: (response: HttpErrorResponse) => {
        if (response.status === 0 || response.status >= 500) { this.reconcile(command); return; }
        this.error.set(this.errorText(response)); this.busy.set(false); this.refresh();
      },
    });
  }

  private reconcile(command: MonitorCommand): void {
    this.api.current().subscribe({
      next: view => {
        this.acceptView(view);
        if (view.lastCommand?.commandId !== command.commandId) {
          this.uncertain = command; this.unresolved.set(true);
          this.error.set('Исход команды пока неизвестен. Состояние перечитывается; новую команду не отправляем.');
        } else this.notice.set('Команда подтверждена сохранённым состоянием монитора.');
        this.busy.set(false);
      },
      error: () => {
        this.uncertain = command; this.unresolved.set(true);
        this.error.set('Исход команды неизвестен: состояние монитора недоступно. Новую команду не отправляем.');
        this.busy.set(false);
      },
    });
  }

  private acceptView(view: MonitorView): void {
    const previous = this.view();
    if (previous && (view.configRevision < previous.configRevision ||
      (view.configRevision === previous.configRevision && previous.aggregate.lastCompletedAt && view.aggregate.lastCompletedAt &&
        view.aggregate.lastCompletedAt < previous.aggregate.lastCompletedAt))) return;
    this.view.set(view);
    if (this.uncertain && view.lastCommand?.commandId === this.uncertain.commandId) {
      this.uncertain = null; this.unresolved.set(false); this.error.set('');
      this.notice.set('Команда подтверждена сохранённым состоянием монитора.');
    }
  }

  private errorText(response: HttpErrorResponse): string {
    switch (response.error?.error) {
      case 'MONITOR_DISABLED': return 'Монитор не включён в backend-конфигурации.';
      case 'CONFIG_REVISION_CONFLICT': return 'Состояние монитора изменилось. Показаны актуальные данные; повторите действие после проверки.';
      case 'INVALID_ARGUMENTS': return 'Выбранный интервал не разрешён backend-конфигурацией.';
      case 'MONITOR_FAULTED': return 'Монитор остановлен из-за ошибки. Проверьте его состояние.';
      default: return 'Не удалось прочитать или изменить состояние монитора.';
    }
  }
}
