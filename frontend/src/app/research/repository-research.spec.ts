import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RepositoryResearch } from './repository-research';

describe('RepositoryResearch', () => {
  let fixture: ComponentFixture<RepositoryResearch>;
  let http: HttpTestingController;
  const ref = '12345678-1234-1234-1234-123456789abc';

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RepositoryResearch],
      providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RepositoryResearch);
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify(); });

  it('runs only on action, blocks duplicate submission and opens an untrusted saved report as text', () => {
    http.expectNone('/api/repository-research');
    const query = fixture.nativeElement.querySelector('input[aria-label="Строка поиска"]') as HTMLInputElement;
    query.value = 'fixture marker'; query.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit', { cancelable: true })); fixture.detectChanges();
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    const request = http.expectOne('/api/repository-research');
    expect(request.request.body).toEqual({ query: 'fixture marker', maxResults: 5 });
    expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);
    request.flush({ status: 'COMPLETED', stepsCompleted: 3, matchesSeen: 5, filesMatched: 2,
      truncated: true, receipt: { reportRef: ref, createdAt: '2026-09-28T00:00:00Z',
        bytesWritten: 50, contentHash: 'a'.repeat(64) } });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Результат усечён');
    (fixture.nativeElement.querySelector('.result button') as HTMLButtonElement).click();
    const report = http.expectOne(`/api/repository-research/reports/${ref}`);
    report.flush({ reportRef: ref, content: '# Repository search\n- src/A.java:7 <img src=x onerror=alert(1)>' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.report pre')?.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(fixture.nativeElement.querySelector('.report img')).toBeNull();
  });

  it('keeps UNKNOWN distinct and does not claim a report is absent', () => {
    fixture.componentInstance.query = 'marker'; fixture.componentInstance.run();
    http.expectOne('/api/repository-research').flush({ status: 'UNKNOWN', failedStep: 'SAVE',
      stepsCompleted: 2, code: 'SAVE_OUTCOME_UNKNOWN' }, { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Результат неизвестен');
    expect(fixture.nativeElement.textContent).toContain('Отчёт мог быть создан');
    expect(fixture.nativeElement.querySelector('.result')).toBeNull();
  });
});
