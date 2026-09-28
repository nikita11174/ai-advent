import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RepositoryMonitor } from './repository-monitor';
import { MonitorView } from './repository-monitor.service';

describe('RepositoryMonitor', () => {
  let fixture: ComponentFixture<RepositoryMonitor>;
  let http: HttpTestingController;
  const view = (revision: number, enabled: boolean, commandId?: string): MonitorView => ({
    monitorId: 'fixture', repositoryRef: 'fixture', enabled, intervalSeconds: enabled ? 10 : null,
    configRevision: revision, nextRunAt: null,
    aggregate: { successCount: 0, failureCount: 0, dirtySampleCount: 0, headTransitionCount: 0,
      branchTransitionCount: 0, firstSuccessAt: null, lastSuccessAt: null, lastCompletedAt: null,
      lastOutcome: null, lastFailureCode: null, latestStatus: null }, latestDigest: null,
    lastCommand: commandId ? { commandId, action: enabled ? 'START' : 'STOP', intervalSeconds: 10,
      operationStatus: 'APPLIED', configRevision: revision } : null,
    health: { status: enabled ? 'WAITING' : 'IDLE', code: null },
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RepositoryMonitor],
      providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RepositoryMonitor);
    fixture.detectChanges();
    http.expectOne('/api/repository-monitor/configuration').flush({ minimumIntervalSeconds: 10,
      maximumIntervalSeconds: 86400, defaultIntervalSeconds: 300 });
    http.expectOne('/api/repository-monitor').flush(view(0, false));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Состояние монитора');
    expect(fixture.nativeElement.textContent).toContain('Остановлен');
    expect((fixture.nativeElement.querySelector('.actions button') as HTMLButtonElement).textContent).toBe('Запустить');
  });

  afterEach(() => { fixture.destroy(); http.verify(); });

  it('sends one command per click, polls by GET, and reloads without mutating', () => {
    const start = fixture.nativeElement.querySelector('.actions button') as HTMLButtonElement;
    start.click(); fixture.detectChanges(); start.click();
    const request = http.expectOne('/api/repository-monitor/start');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.expectedRevision).toBe(0);
    expect(request.request.body.intervalSeconds).toBe(10);
    const commandId = request.request.body.commandId;
    expect(commandId).toMatch(/^[a-f\d-]{36}$/);
    request.flush({ operationStatus: 'APPLIED', view: view(1, true, commandId) });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ожидание следующей проверки');
    expect((fixture.nativeElement.querySelector('.actions button') as HTMLButtonElement).textContent).toBe('Остановить');
    fixture.componentInstance.refresh();
    http.expectOne('/api/repository-monitor').flush(view(1, true, commandId));
    const stop = fixture.nativeElement.querySelector('.actions button') as HTMLButtonElement;
    stop.click(); fixture.detectChanges(); stop.click();
    const stopRequest = http.expectOne('/api/repository-monitor/stop');
    expect(stopRequest.request.body.expectedRevision).toBe(1);
    stopRequest.flush({ operationStatus: 'APPLIED', view: view(2, false, stopRequest.request.body.commandId) });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Монитор остановлен');
    fixture.destroy();
    const reopened = TestBed.createComponent(RepositoryMonitor);
    reopened.detectChanges();
    http.expectOne('/api/repository-monitor/configuration').flush({ minimumIntervalSeconds: 10,
      maximumIntervalSeconds: 86400, defaultIntervalSeconds: 300 });
    http.expectOne('/api/repository-monitor').flush(view(2, false));
    http.expectNone('/api/repository-monitor/start');
    reopened.destroy();
  });

  it('reconciles unknown outcome by read and blocks a new command while unresolved', () => {
    fixture.componentInstance.start();
    const request = http.expectOne('/api/repository-monitor/start');
    request.flush({ operationStatus: 'UNKNOWN', view: null }, { status: 202, statusText: 'Accepted' });
    http.expectOne('/api/repository-monitor').flush(view(0, false));
    fixture.detectChanges();
    expect(fixture.componentInstance.unresolved()).toBe(true);
    fixture.componentInstance.start();
    http.expectNone('/api/repository-monitor/start');
    fixture.componentInstance.refresh();
    http.expectOne('/api/repository-monitor').flush(view(1, true, request.request.body.commandId));
    expect(fixture.componentInstance.unresolved()).toBe(false);
  });

  it('shows a backend refusal without assuming the monitor started', () => {
    fixture.componentInstance.start();
    http.expectOne('/api/repository-monitor/start').flush({ error: 'MONITOR_DISABLED' },
      { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/repository-monitor').flush(view(0, false));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('не включён');
    expect(fixture.nativeElement.textContent).toContain('Монитор остановлен');
  });
});
