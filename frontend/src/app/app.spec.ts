import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders the Russian product shell', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Engineering Review Mentor');
    expect(fixture.nativeElement.textContent).toContain('AI Advent · День 1');
    expect(fixture.nativeElement.textContent).toContain('Вставьте Java-код или задайте инженерный вопрос');
  });

  it('adds the user message and loading state before rendering a Markdown response', () => {
    const fixture = TestBed.createComponent(App);
    const component = fixture.componentInstance as unknown as { input: string; analyze(): void };
    component.input = 'class Example {}';

    component.analyze();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('class Example {}');
    expect(fixture.nativeElement.textContent).toContain('Ментор анализирует...');

    http.expectOne('/api/review').flush({ analysis: '## Риски\n\n- Деление на `values.size()`\n- Пустой список' });
    fixture.detectChanges();

    const response = fixture.nativeElement.querySelector('.analysis-text');
    expect(response.querySelector('h2')?.textContent).toBe('Риски');
    expect(response.querySelectorAll('li')).toHaveLength(2);
    expect(response.querySelector('code')?.textContent).toBe('values.size()');
  });

  it('keeps multiple exchanges rendered in order', () => {
    const fixture = TestBed.createComponent(App);
    const component = fixture.componentInstance as unknown as { input: string; analyze(): void };

    component.input = 'Первый вопрос';
    component.analyze();
    http.expectOne('/api/review').flush({ analysis: 'Первый ответ' });
    component.input = 'Второй вопрос';
    component.analyze();
    http.expectOne('/api/review').flush({ analysis: 'Второй ответ' });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text.indexOf('Первый вопрос')).toBeLessThan(text.indexOf('Первый ответ'));
    expect(text.indexOf('Первый ответ')).toBeLessThan(text.indexOf('Второй вопрос'));
    expect(text.indexOf('Второй вопрос')).toBeLessThan(text.indexOf('Второй ответ'));
  });

  it('submits with Ctrl+Enter', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('textarea');
    textarea.value = 'Вопрос по коду';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true }));

    expect(http.expectOne('/api/review').request.body).toEqual({ input: 'Вопрос по коду' });
  });

  it('does not render dangerous HTML from the mentor response', () => {
    const fixture = TestBed.createComponent(App);
    const component = fixture.componentInstance as unknown as { input: string; analyze(): void };
    component.input = 'class Example {}';
    component.analyze();
    http.expectOne('/api/review').flush({
      analysis: '<script>alert(1)</script><img src=x onerror="alert(2)">\n\n[опасная ссылка](javascript:alert(3))',
    });
    fixture.detectChanges();

    const response = fixture.nativeElement.querySelector('.analysis-text');
    expect(response.querySelector('script')).toBeNull();
    expect(response.querySelector('img')).toBeNull();
    expect(response.querySelector('[onerror]')).toBeNull();
    expect(response.querySelector('a')?.getAttribute('href')).not.toMatch(/^javascript:/i);
  });

  it('renders a Russian network error in the mentor message', () => {
    const fixture = TestBed.createComponent(App);
    const component = fixture.componentInstance as unknown as { input: string; analyze(): void };
    component.input = 'question';
    component.analyze();
    http.expectOne('/api/review').flush(null, { status: 0, statusText: 'Unknown Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Не удалось подключиться к серверу.');
  });
});
