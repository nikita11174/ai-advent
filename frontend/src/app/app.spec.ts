import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { App } from './app';

interface TestApp {
  input: string;
  selectedMode: 'FREE' | 'CONTROLLED';
  controls: {
    maxTokens: number; maxFindings: number; summaryMaxWords: number;
    reasonMaxWords: number; recommendationMaxWords: number; terminationInstruction: string;
  };
  analyze(): void;
  compare(): void;
  resetControls(): void;
  toggleRaw(id: number): void;
}

const controlledResponse = (rawResponse = '{"summary":"Резюме","findings":[],"recommendation":"Проверить тесты"}') => ({
  review: { summary: 'Резюме', findings: [], recommendation: 'Проверить тесты' },
  rawResponse,
});

describe('App', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<App>;
  let component: TestApp;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance as unknown as TestApp;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('renders the Russian Day 2 shell and mode selector', () => {
    expect(fixture.nativeElement.textContent).toContain('Engineering Review Mentor');
    expect(fixture.nativeElement.textContent).toContain('AI Advent · День 2');
    expect(fixture.nativeElement.textContent).toContain('Свободный');
    expect(fixture.nativeElement.textContent).toContain('Контролируемый');
  });

  it('submits FREE mode and renders safe Markdown', () => {
    component.input = 'class Example {}';
    component.analyze();
    const request = http.expectOne('/api/review');
    expect(request.request.body).toEqual({ input: 'class Example {}', mode: 'FREE' });
    request.flush({ analysis: '## Риски\n\n- Деление на `size`' });
    fixture.detectChanges();

    const response = fixture.nativeElement.querySelector('.analysis-text');
    expect(response.querySelector('h2')?.textContent).toBe('Риски');
    expect(response.querySelector('code')?.textContent).toBe('size');
  });

  it('keeps unsafe FREE Markdown HTML and URLs inert', () => {
    component.input = 'code';
    component.analyze();
    http.expectOne('/api/review').flush({
      analysis: '<script>alert(1)</script><img src=x onerror="alert(2)">\n[link](javascript:alert(3))',
    });
    fixture.detectChanges();

    const response = fixture.nativeElement.querySelector('.analysis-text');
    expect(response.querySelector('script')).toBeNull();
    expect(response.querySelector('img')).toBeNull();
    expect(response.querySelector('[onerror]')).toBeNull();
    expect(response.querySelector('a')?.getAttribute('href')).not.toMatch(/^javascript:/i);
  });

  it('shows controlled defaults and restores edited settings', () => {
    component.selectedMode = 'CONTROLLED';
    fixture.detectChanges();
    expect(component.controls.maxTokens).toBe(600);
    expect(component.controls.maxFindings).toBe(3);
    expect(component.controls.summaryMaxWords).toBe(30);
    expect(component.controls.reasonMaxWords).toBe(30);
    expect(component.controls.recommendationMaxWords).toBe(30);
    component.controls.maxTokens = 450;
    component.controls.maxFindings = 1;
    component.resetControls();
    expect(component.controls.maxTokens).toBe(600);
    expect(component.controls.maxFindings).toBe(3);
    expect(fixture.nativeElement.querySelector('input[readonly]').value).toBe('JSON');
  });

  it('submits edited CONTROLLED settings and renders structured result', () => {
    component.selectedMode = 'CONTROLLED';
    component.controls.maxTokens = 450;
    component.controls.maxFindings = 1;
    component.input = 'code';
    component.analyze();
    const request = http.expectOne('/api/review');
    expect(request.request.body.mode).toBe('CONTROLLED');
    expect(request.request.body.controls.maxTokens).toBe(450);
    expect(request.request.body.controls.maxFindings).toBe(1);
    request.flush(controlledResponse());
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Краткое резюме');
    expect(fixture.nativeElement.textContent).toContain('Существенных рисков не найдено.');
    expect(fixture.nativeElement.textContent).toContain('Рекомендация');
  });

  it('expands raw JSON as escaped plain text', () => {
    const raw = '{"summary":"<img src=x onerror=alert(1)>","findings":[],"recommendation":"ok"}';
    component.selectedMode = 'CONTROLLED';
    component.input = 'code';
    component.analyze();
    http.expectOne('/api/review').flush(controlledResponse(raw));
    fixture.detectChanges();

    component.toggleRaw(1);
    fixture.detectChanges();
    const rawBlock = fixture.nativeElement.querySelector('.raw-json');
    expect(rawBlock.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(rawBlock.querySelector('img')).toBeNull();
    component.toggleRaw(1);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.raw-json')).toBeNull();
  });

  it('compares identical input once with a controls snapshot', () => {
    component.controls.maxTokens = 450;
    component.controls.maxFindings = 1;
    component.input = 'same exact code';
    component.compare();
    const requests = http.match('/api/review');
    expect(requests).toHaveLength(2);
    expect(requests[0].request.body.input).toBe('same exact code');
    expect(requests[1].request.body.input).toBe('same exact code');
    const free = requests.find((request) => request.request.body.mode === 'FREE')!;
    const controlled = requests.find((request) => request.request.body.mode === 'CONTROLLED')!;
    expect(free.request.body.controls).toBeUndefined();
    expect(controlled.request.body.controls.maxTokens).toBe(450);
    free.flush({ analysis: 'Свободный ответ' });
    controlled.flush(controlledResponse());
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.user-message')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Свободный ответ');
    expect(fixture.nativeElement.textContent).toContain('Краткое резюме');
    expect(fixture.nativeElement.textContent).toContain('JSON · max 450 tokens · до 1 замечаний');
  });

  it('keeps old comparison metadata after settings change', () => {
    component.controls.maxTokens = 450;
    component.input = 'code';
    component.compare();
    const requests = http.match('/api/review');
    requests.find((request) => request.request.body.mode === 'FREE')!.flush({ analysis: 'Ответ' });
    requests.find((request) => request.request.body.mode === 'CONTROLLED')!.flush(controlledResponse());
    component.controls.maxTokens = 900;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('JSON · max 450 tokens');
    expect(fixture.nativeElement.textContent).not.toContain('JSON · max 900 tokens');
  });

  it('keeps successful comparison side when controlled side fails and exposes raw content', () => {
    component.input = 'code';
    component.compare();
    const requests = http.match('/api/review');
    requests.find((request) => request.request.body.mode === 'FREE')!.flush({ analysis: 'Успешный ответ' });
    requests.find((request) => request.request.body.mode === 'CONTROLLED')!.flush(
      { error: 'invalid JSON', rawResponse: '<b>broken</b>' }, { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Успешный ответ');
    expect(fixture.nativeElement.textContent).toContain('Контролируемый ответ не прошёл проверку');
    expect(fixture.nativeElement.textContent).toContain('Показать JSON');
  });

  it('submits the selected mode with Ctrl+Enter', () => {
    component.selectedMode = 'CONTROLLED';
    const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('.composer > textarea');
    textarea.value = 'Вопрос по коду';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true }));

    expect(http.expectOne('/api/review').request.body.mode).toBe('CONTROLLED');
  });

  it('renders a Russian network error inline', () => {
    component.input = 'question';
    component.analyze();
    http.expectOne('/api/review').flush(null, { status: 0, statusText: 'Unknown Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Не удалось подключиться к серверу.');
  });
});
