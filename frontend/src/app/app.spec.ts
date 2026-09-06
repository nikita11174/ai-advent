import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { App } from './app';

interface TestApp {
  input: string;
  experiment: 'FORMAT' | 'REASONING' | 'TEMPERATURE' | 'MODELS';
  selectedModelKey: string;
  selectModels(): void;
  compareModels(): void;
  updateModelConclusion(id: number, value: string): void;
  selectedMode: 'FREE' | 'CONTROLLED';
  selectedStrategy: 'DIRECT' | 'STEP_BY_STEP' | 'SELF_PROMPT' | 'EXPERTS';
  selectedTemperature: 0 | 0.7 | 1.2;
  controls: { maxTokens: number; maxFindings: number };
  analyze(): void;
  compare(): void;
  compareStrategies(): void;
  compareTemperatures(): void;
  updateTemperatureConclusion(id: number, field: 'accuracy' | 'creativity' | 'diversity' | 'taskFit', value: string): void;
  saveEvaluation(): void;
  resetControls(): void;
  toggleRaw(id: number): void;
  togglePrompt(id: number, strategy: 'SELF_PROMPT'): void;
  newDialog(): void;
  openDialog(id: string): void;
  exchanges(): readonly { temperatureConclusion?: unknown }[];
}

const now = '2026-09-03T10:00:00Z';
const dialog = (id = '11111111-1111-1111-1111-111111111111', exchanges: unknown[] = [], ui?: unknown) => ({
  id, title: 'Новый диалог', createdAt: now, updatedAt: now, state: { exchanges, ui },
});
const controlled = (rawResponse = '{"summary":"Резюме","findings":[],"recommendation":"Проверить"}') => ({
  review: { summary: 'Резюме', findings: [], recommendation: 'Проверить' }, rawResponse,
});
const profiles = ['Luna', 'Terra', 'Sol'].map((name, index) => ({
  key: ['WEAK', 'MEDIUM', 'STRONG'][index], label: `GPT-5.6 ${name}`, provider: 'OPENAI',
  modelId: `gpt-5.6-${name.toLowerCase()}`, modelUrl: `https://developers.openai.com/api/docs/models/gpt-5.6-${name.toLowerCase()}`,
}));
const modelResponse = (index: number) => ({ model: profiles[index], returnedModel: profiles[index].modelId,
  status: 'completed', incompleteReason: null, serviceTier: 'default', apiLatencyMs: 123, startedAt: now,
  analysis: '## Анализ\n\n- Риск\n<img src=x onerror=alert(1)>', error: null,
  usage: { inputTokens: 100, outputTokens: 20, totalTokens: 120, cachedInputTokens: 40, cacheWriteInputTokens: 10, reasoningTokens: 0 },
  cost: { status: 'ESTIMATED', amount: '0.0000373', reason: null, snapshot: { id: 'test-price', currency: 'USD' } },
  configuration: { version: 'test-preset' },
});

describe('App', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<App>;
  let component: TestApp;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance as unknown as TestApp;
    fixture.detectChanges();
    http.expectOne('/api/dialogs').flush([]);
    http.expectOne('/api/dialogs').flush(dialog());
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function flushSave(): void { http.expectOne(request => request.url.startsWith('/api/dialogs/')).flush(dialog()); }

  it('selects the model experiment and sends only the selected model and exact input', () => {
    component.selectModels(); http.expectOne('/api/model-options').flush(profiles);
    component.selectedModelKey = 'MEDIUM'; component.input = ' exact\ncode '; fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('OpenAI · GPT-5.6');
    component.analyze(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-model-result mat-progress-bar')).not.toBeNull();
    const request = http.expectOne('/api/model-review');
    expect(request.request.body).toEqual({ input: ' exact\ncode ', modelKey: 'MEDIUM' });
    request.flush(modelResponse(1)); flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-model-result')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('123 мс');
    expect(fixture.nativeElement.textContent).toContain('0.0000373 USD');
    expect(fixture.nativeElement.querySelector('app-model-result h2')?.textContent).toBe('Анализ');
    expect(fixture.nativeElement.querySelector('app-model-result img')).toBeNull();
  });

  it('compares models independently and restores immutable metrics and conclusions', () => {
    component.selectModels(); http.expectOne('/api/model-options').flush(profiles);
    component.input = 'same exact input'; component.compareModels(); fixture.detectChanges();
    const requests = http.match('/api/model-review');
    expect(requests.map(r => r.request.body)).toEqual(profiles.map(p => ({ input: 'same exact input', modelKey: p.key })));
    expect(fixture.nativeElement.querySelectorAll('.user-message')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('app-model-result mat-progress-bar')).toHaveLength(3);
    requests[0].flush(modelResponse(0)); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-model-result mat-progress-bar')).toHaveLength(2);
    requests[1].flush({ error: 'Лимит провайдера' }, { status: 502, statusText: 'Bad Gateway' });
    requests[2].flush(modelResponse(2));
    const saved = http.expectOne(r => r.method === 'PUT');
    const state = structuredClone(saved.request.body.state); saved.flush(dialog());
    component.selectedModelKey = 'STRONG'; fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-model-result h2')).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('Лимит провайдера');
    expect(state.exchanges[0].models.map((m: {label: string}) => m.label)).toEqual(profiles.map(p => p.label));
    component.openDialog('22222222-2222-2222-2222-222222222222');
    http.expectOne('/api/dialogs/22222222-2222-2222-2222-222222222222').flush(dialog(
      '22222222-2222-2222-2222-222222222222', state.exchanges, state.ui)); fixture.detectChanges();
    expect(component.experiment).toBe('MODELS'); expect(component.selectedModelKey).toBe('WEAK');
    expect(fixture.nativeElement.querySelectorAll('app-model-result')).toHaveLength(3);
    component.updateModelConclusion(state.exchanges[0].id, 'Ничья по качеству'); component.saveEvaluation();
    const update = http.expectOne(r => r.method === 'PUT');
    expect(update.request.body.state.exchanges[0].modelConclusion).toBe('Ничья по качеству');
    expect(update.request.body.state.exchanges[0].modelResults.WEAK.response.usage.totalTokens).toBe(120);
    update.flush(dialog());
  });

  it('renders unknown model metadata and preserves error-side response evidence', () => {
    component.selectModels(); http.expectOne('/api/model-options').flush(profiles);
    component.input = 'x'; component.analyze();
    const response = { ...modelResponse(0), error: 'Неполный ответ', status: 'incomplete', analysis: null,
      incompleteReason: 'max_output_tokens', apiLatencyMs: null, returnedModel: null,
      usage: { inputTokens: null, outputTokens: null, totalTokens: null, cachedInputTokens: null, cacheWriteInputTokens: null, reasoningTokens: null },
      cost: { status: 'UNKNOWN', amount: null, reason: 'usage отсутствует', snapshot: { id: 'test-price', currency: 'USD' } } };
    http.expectOne('/api/model-review').flush(response, { status: 502, statusText: 'Bad Gateway' });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('UNKNOWN');
    expect(fixture.nativeElement.textContent).toContain('max_output_tokens');
    expect(fixture.nativeElement.textContent).toContain('Неполный ответ');
    expect(fixture.nativeElement.textContent).not.toContain('0 USD');
  });

  it('keeps Day 1 FREE Markdown safe and persistent', () => {
    component.input = 'class Example {}'; component.analyze();
    http.expectOne('/api/review').flush({ analysis: '## Риски\n\n- `size`\n<img src=x onerror=alert(1)>' });
    flushSave(); fixture.detectChanges();
    const response = fixture.nativeElement.querySelector('.analysis-text');
    expect(response.querySelector('h2')?.textContent).toBe('Риски');
    expect(response.querySelector('code')?.textContent).toBe('size');
    expect(response.querySelector('img')).toBeNull();
  });

  it('keeps Day 2 CONTROLLED rendering and raw JSON inert', () => {
    const raw = '{"summary":"<img src=x onerror=alert(1)>","findings":[],"recommendation":"ok"}';
    component.selectedMode = 'CONTROLLED'; component.input = 'code'; component.analyze();
    http.expectOne('/api/review').flush(controlled(raw)); flushSave(); fixture.detectChanges();
    component.toggleRaw(1); fixture.detectChanges();
    const rawBlock = fixture.nativeElement.querySelector('.raw-json');
    expect(rawBlock.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(rawBlock.querySelector('img')).toBeNull();
  });

  it('keeps Day 2 comparison input and controls snapshot', () => {
    component.controls.maxTokens = 450; component.controls.maxFindings = 1; component.input = 'same'; component.compare();
    const requests = http.match('/api/review');
    expect(requests).toHaveLength(2);
    expect(requests.every(request => request.request.body.input === 'same')).toBe(true);
    requests.find(request => request.request.body.mode === 'FREE')!.flush({ analysis: 'free' });
    requests.find(request => request.request.body.mode === 'CONTROLLED')!.flush(controlled());
    flushSave(); component.controls.maxTokens = 900; fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.user-message')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('JSON · max 450 tokens');
  });

  it('runs selected Day 3 strategy and exposes generated prompt', () => {
    component.experiment = 'REASONING'; component.selectedStrategy = 'SELF_PROMPT'; component.input = 'task'; component.analyze();
    const request = http.expectOne('/api/reasoning-review');
    expect(request.request.body).toEqual({ input: 'task', strategy: 'SELF_PROMPT' });
    request.flush({ strategy: 'SELF_PROMPT', analysis: 'answer', generatedPrompt: '<b>prompt</b>' });
    flushSave(); fixture.detectChanges();
    component.togglePrompt(1, 'SELF_PROMPT'); fixture.detectChanges();
    const prompt = fixture.nativeElement.querySelector('.raw-json');
    expect(prompt.textContent).toContain('<b>prompt</b>');
    expect(prompt.querySelector('b')).toBeNull();
  });

  it('compares all strategies with one immutable input and independent results', () => {
    component.experiment = 'REASONING'; component.input = 'same task'; component.compareStrategies();
    const requests = http.match('/api/reasoning-review');
    expect(requests).toHaveLength(4);
    expect(requests.every(request => request.request.body.input === 'same task')).toBe(true);
    for (const request of requests) request.flush({ strategy: request.request.body.strategy, analysis: request.request.body.strategy });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.user-message')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('.strategy-card')).toHaveLength(4);
  });

  it('preserves successful strategies when one comparison request fails', () => {
    component.experiment = 'REASONING'; component.input = 'task'; component.compareStrategies();
    const requests = http.match('/api/reasoning-review');
    requests[0].flush({ strategy: 'DIRECT', analysis: 'success' });
    requests[1].flush({ error: 'failure' }, { status: 502, statusText: 'Bad Gateway' });
    requests[2].flush({ strategy: 'SELF_PROMPT', analysis: 'self', generatedPrompt: 'prompt' });
    requests[3].flush({ strategy: 'EXPERTS', analysis: 'experts' });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('success');
    expect(fixture.nativeElement.textContent).toContain('failure');
  });

  it('runs the selected Day 4 temperature with the exact input', () => {
    component.experiment = 'TEMPERATURE'; component.selectedTemperature = 0.7; component.input = 'exact task'; component.analyze();
    const request = http.expectOne('/api/temperature-review');
    expect(request.request.body).toEqual({ input: 'exact task', temperature: 0.7 });
    request.flush({ temperature: 0.7, analysis: '## Ответ' });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Temperature 0.7');
    expect(fixture.nativeElement.querySelector('.analysis-text h2')?.textContent).toBe('Ответ');
  });

  it('compares all Day 4 temperatures independently with one input', () => {
    component.experiment = 'TEMPERATURE'; component.input = 'same task'; component.compareTemperatures();
    const requests = http.match('/api/temperature-review');
    expect(requests).toHaveLength(3);
    expect(requests.map(request => request.request.body.temperature)).toEqual([0, 0.7, 1.2]);
    expect(requests.every(request => request.request.body.input === 'same task')).toBe(true);
    requests[0].flush({ temperature: 0, analysis: 'zero' });
    requests[1].flush({ error: 'failure' }, { status: 502, statusText: 'Bad Gateway' });
    requests[2].flush({ temperature: 1.2, analysis: 'high' });
    const save = http.expectOne(request => request.url.startsWith('/api/dialogs/'));
    expect(save.request.body.state.ui.experiment).toBe('TEMPERATURE');
    save.flush(dialog()); fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.user-message')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('.temperature-comparison .strategy-card')).toHaveLength(3);
    expect(fixture.nativeElement.textContent).toContain('zero');
    expect(fixture.nativeElement.textContent).toContain('failure');
    expect(fixture.nativeElement.textContent).toContain('high');
  });

  it('restores the Day 4 experiment and four human evaluation fields', () => {
    const exchange = { id: 4, input: 'benchmark', mode: 'TEMPERATURE_COMPARE',
      temperatureResults: { 0: { loading: false, analysis: 'zero' }, 0.7: { loading: false, analysis: 'balanced' }, 1.2: { loading: false, analysis: 'creative' } },
      temperatureConclusion: { accuracy: 'Точно', creativity: 'Полезные идеи', diversity: 'Разные ответы', taskFit: 'Code review' } };
    component.openDialog('22222222-2222-2222-2222-222222222222');
    http.expectOne('/api/dialogs/22222222-2222-2222-2222-222222222222').flush(dialog(
      '22222222-2222-2222-2222-222222222222', [exchange],
      { experiment: 'TEMPERATURE', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 1.2 }));
    fixture.detectChanges();

    expect(component.experiment).toBe('TEMPERATURE');
    expect(component.selectedTemperature).toBe(1.2);
    expect(component.exchanges()[0].temperatureConclusion).toEqual(exchange.temperatureConclusion);
    const fields = [...fixture.nativeElement.querySelectorAll('.conclusion-grid textarea')] as HTMLTextAreaElement[];
    expect(fields.map(field => field.value)).toEqual(['Точно', 'Полезные идеи', 'Разные ответы', 'Code review']);

    component.updateTemperatureConclusion(4, 'accuracy', 'Обновлено'); component.saveEvaluation();
    const save = http.expectOne('/api/dialogs/22222222-2222-2222-2222-222222222222');
    expect(save.request.body.state.ui.experiment).toBe('TEMPERATURE');
    expect(save.request.body.state.ui.selectedTemperature).toBe(1.2);
    expect(save.request.body.state.exchanges[0].temperatureConclusion.accuracy).toBe('Обновлено');
    save.flush(dialog());
  });

  it('creates and restores dialogs with completed exchanges', () => {
    component.newDialog();
    http.expectOne('/api/dialogs').flush(dialog('22222222-2222-2222-2222-222222222222'));
    component.openDialog('11111111-1111-1111-1111-111111111111');
    http.expectOne('/api/dialogs/11111111-1111-1111-1111-111111111111').flush(dialog(undefined, [{ id: 7, input: 'restored', mode: 'FREE', free: { loading: false, analysis: 'saved' } }]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('restored');
    expect(fixture.nativeElement.textContent).toContain('saved');
  });

  it('restores controlled defaults', () => {
    component.controls.maxTokens = 450; component.controls.maxFindings = 1; component.resetControls();
    expect(component.controls.maxTokens).toBe(600); expect(component.controls.maxFindings).toBe(3);
  });

  it('submits with Ctrl+Enter', () => {
    const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('.composer > textarea');
    textarea.value = 'question'; textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true }));
    http.expectOne('/api/review').flush({ analysis: 'answer' }); flushSave();
  });
});
