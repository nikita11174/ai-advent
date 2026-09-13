import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { App } from './app';

interface TestApp {
  input: string;
  experiment: 'FORMAT' | 'REASONING' | 'TEMPERATURE' | 'MODELS' | 'AGENT';
  selectedModelKey: string;
  backendStatus(): 'CONNECTING' | 'ONLINE' | 'OFFLINE';
  checkBackend(): void;
  selectModels(): void;
  compareModels(): void;
  updateModelConclusion(id: number, value: string): void;
  selectedMode: 'FREE' | 'CONTROLLED';
  selectedStrategy: 'DIRECT' | 'STEP_BY_STEP' | 'SELF_PROMPT' | 'EXPERTS';
  selectedTemperature: 0 | 0.7 | 1.2;
  contextMode: 'FULL' | 'SUMMARY_RECENT' | 'SLIDING_WINDOW' | 'STICKY_FACTS';
  recentMessageCount: number;
  branchId: string | null;
  checkpointId: string | null;
  topologyError(): string;
  selectLinearContextMode(mode: 'FULL' | 'SUMMARY_RECENT' | 'SLIDING_WINDOW' | 'STICKY_FACTS'): void;
  selectAgent(): void;
  createCheckpoint(): void;
  createBranch(): void;
  switchBranch(branchId: string | null): void;
  selectCheckpoint(checkpointId: string | null): void;
  branches(): readonly { id: string; checkpointId: string; history: unknown[] }[];
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

  afterEach(() => http.verify({ ignoreCancelled: true }));

  function flushSave(): void { http.expectOne(request => request.url.startsWith('/api/dialogs/')).flush(dialog()); }
  function flushTopology(id = dialog().id, branches: unknown[] = [], checkpoints: unknown[] = []): void {
    http.expectOne(`/api/dialogs/${id}/agent/branches`).flush(branches);
    http.expectOne(`/api/dialogs/${id}/agent/checkpoints`).flush(checkpoints);
  }
  function flushHealth(): void { http.expectOne('/api/health').flush({ status: 'UP' }); }

  it('sends Agent follow-ups by dialog, restores the UI archive and isolates A/B/A', () => {
    const a = dialog().id;
    const b = '22222222-2222-2222-2222-222222222222';
    const agentButton = [...fixture.nativeElement.querySelectorAll('.experiment-selector button')]
      .find((button: HTMLButtonElement) => button.textContent?.trim() === 'Агент') as HTMLButtonElement;
    agentButton.click(); fixture.detectChanges();
    flushHealth();
    flushTopology(a);
    expect(component.experiment).toBe('AGENT');
    expect(fixture.nativeElement.querySelector('.controls-panel')).toBeNull();
    expect(fixture.nativeElement.querySelector('.temperature-selector')).toBeNull();
    expect(fixture.nativeElement.querySelector('.action-buttons').textContent).not.toContain('Сравнить');
    expect(fixture.nativeElement.textContent).toContain('восстанавливает его после перезапуска');

    component.input = 'fact A'; component.analyze(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('mat-progress-bar')).not.toBeNull();
    component.analyze();
    const first = http.expectOne(`/api/dialogs/${a}/agent/messages`);
    expect(first.request.body).toEqual({ input: 'fact A', contextMode: 'FULL', recentMessageCount: 4 });
    first.flush({ analysis: '## Запомнил\n<img src=x onerror=alert(1)>', metrics: {
      currentRequestTokens: 2, contextTokens: 12, responseTokens: 5,
      providerUsage: { promptTokens: 20, completionTokens: 6, totalTokens: 26 },
    }, summaryMetrics: null, contextMetadata: { mode: 'FULL', recentMessageCount: 4, summary: null, summarizedMessageCount: 0 } });
    const savedFirst = http.expectOne(r => r.method === 'PUT');
    savedFirst.flush(dialog(a, savedFirst.request.body.state.exchanges, savedFirst.request.body.state.ui));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.analysis-text h2')?.textContent).toBe('Запомнил');
    expect(fixture.nativeElement.querySelector('.analysis-text img')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('локальная оценка, не токенизация провайдера');
    expect(fixture.nativeElement.textContent).toContain('2 / 12 / 5');
    expect(fixture.nativeElement.textContent).toContain('20 / 6 / 26');

    component.input = 'follow-up A'; component.analyze();
    const followUp = http.expectOne(`/api/dialogs/${a}/agent/messages`);
    expect(followUp.request.body).toEqual({ input: 'follow-up A', contextMode: 'FULL', recentMessageCount: 4 });
    followUp.flush({ analysis: 'answer A' });
    const save = http.expectOne(r => r.method === 'PUT');
    const archive = structuredClone(save.request.body.state);
    expect(archive.ui.experiment).toBe('AGENT');
    expect(archive.exchanges.map((entry: { mode: string }) => entry.mode)).toEqual(['AGENT', 'AGENT']);
    save.flush(dialog(a, archive.exchanges, archive.ui));

    component.newDialog(); http.expectOne('/api/dialogs').flush(dialog(b));
    component.experiment = 'AGENT'; component.input = 'question B'; component.analyze();
    const requestB = http.expectOne(`/api/dialogs/${b}/agent/messages`);
    expect(requestB.request.body).toEqual({ input: 'question B', contextMode: 'FULL', recentMessageCount: 4 });
    requestB.flush({ analysis: 'no context B' });
    const saveB = http.expectOne(r => r.method === 'PUT');
    expect(saveB.request.body.state.exchanges).toHaveLength(1);
    saveB.flush(dialog(b, saveB.request.body.state.exchanges, saveB.request.body.state.ui));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('fact A');

    component.openDialog(a); http.expectOne(`/api/dialogs/${a}`).flush(dialog(a, archive.exchanges, archive.ui));
    flushHealth();
    flushTopology(a);
    fixture.detectChanges();
    expect(component.experiment).toBe('AGENT');
    expect(fixture.nativeElement.textContent).toContain('fact A');
    expect(fixture.nativeElement.textContent).not.toContain('question B');
    component.input = 'back to A'; component.analyze();
    const returnA = http.expectOne(`/api/dialogs/${a}/agent/messages`);
    expect(returnA.request.body).toEqual({ input: 'back to A', contextMode: 'FULL', recentMessageCount: 4 });
    returnA.flush({ analysis: 'still A' }); flushSave();
  });

  it('shows backend availability without agent requests or overlapping heartbeats', () => {
    component.selectAgent(); fixture.detectChanges();
    expect(component.backendStatus()).toBe('CONNECTING');
    expect(fixture.nativeElement.querySelector('.server-status')?.textContent).toContain('Подключение');
    const first = http.expectOne('/api/health');
    expect(first.request.method).toBe('GET');
    component.checkBackend(); http.expectNone('/api/health');
    first.flush({ status: 'UP' }); flushTopology(); fixture.detectChanges();
    expect(component.backendStatus()).toBe('ONLINE');
    expect(fixture.nativeElement.querySelector('.server-status.online')?.textContent).toContain('Сервер подключён');

    component.checkBackend();
    http.expectOne('/api/health').flush(null, { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    expect(component.backendStatus()).toBe('OFFLINE');
    expect(fixture.nativeElement.querySelector('.server-status.offline')?.textContent).toContain('Сервер недоступен');

    component.checkBackend(); http.expectOne('/api/health').flush({ status: 'UP' });
    fixture.detectChanges();
    expect(component.backendStatus()).toBe('ONLINE');
    expect(component.exchanges()).toHaveLength(0);
    http.expectNone(request => request.url.includes('/agent/messages'));
  });

  it('shows Agent context overflow and permits the next manual send without retrying', () => {
    component.experiment = 'AGENT'; component.input = 'failed'; component.analyze();
    const route = `/api/dialogs/${dialog().id}/agent/messages`;
    http.expectOne(route).flush({ error: 'Estimated context limit exceeded: 12 > 10.' }, { status: 413, statusText: 'Payload Too Large' });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('Estimated context limit exceeded');
    http.expectNone(route);
    component.input = 'next'; component.analyze();
    const request = http.expectOne(route);
    expect(request.request.body).toEqual({ input: 'next', contextMode: 'FULL', recentMessageCount: 4 });
    request.flush({ analysis: 'recovered' }); flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('recovered');
  });

  it('sends Summary Recent settings and renders separate summary metrics', () => {
    component.experiment = 'AGENT'; component.contextMode = 'SUMMARY_RECENT'; component.recentMessageCount = 3;
    component.input = 'compare context'; component.analyze();
    const request = http.expectOne(`/api/dialogs/${dialog().id}/agent/messages`);
    expect(request.request.body).toEqual({ input: 'compare context', contextMode: 'SUMMARY_RECENT', recentMessageCount: 3 });
    request.flush({ analysis: 'compressed answer', metrics: {
      currentRequestTokens: 4, contextTokens: 18, responseTokens: 6,
      providerUsage: { promptTokens: 30, completionTokens: 8, totalTokens: 38 },
    }, summaryMetrics: {
      currentRequestTokens: 9, contextTokens: 14, responseTokens: 5,
      providerUsage: { promptTokens: 20, completionTokens: 5, totalTokens: 25 },
    }, contextMetadata: { mode: 'SUMMARY_RECENT', recentMessageCount: 3, summary: 'old facts', summarizedMessageCount: 2 } });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('SUMMARY_RECENT');
    expect(fixture.nativeElement.textContent).toContain('сжато сообщений: 2');
    expect(fixture.nativeElement.textContent).toContain('old facts');
    expect(fixture.nativeElement.textContent).toContain('Summary generation');
  });

  it('sends Sliding Window and Sticky Facts strategies with the selected window', () => {
    component.experiment = 'AGENT'; component.contextMode = 'SLIDING_WINDOW'; component.recentMessageCount = 2;
    component.input = 'window probe'; component.analyze();
    let request = http.expectOne(`/api/dialogs/${dialog().id}/agent/messages`);
    expect(request.request.body).toEqual({ input: 'window probe', contextMode: 'SLIDING_WINDOW', recentMessageCount: 2 });
    request.flush({ analysis: 'window answer', metrics: { currentRequestTokens: 1, contextTokens: 5, responseTokens: 2 },
      summaryMetrics: null, factsMetrics: [], contextMetadata: { mode: 'SLIDING_WINDOW', recentMessageCount: 2, summary: null, summarizedMessageCount: 0 } });
    flushSave();

    component.contextMode = 'STICKY_FACTS'; component.recentMessageCount = 3; component.input = 'facts probe'; component.analyze();
    request = http.expectOne(`/api/dialogs/${dialog().id}/agent/messages`);
    expect(request.request.body).toEqual({ input: 'facts probe', contextMode: 'STICKY_FACTS', recentMessageCount: 3 });
    request.flush({ analysis: 'facts answer', metrics: { currentRequestTokens: 1, contextTokens: 7, responseTokens: 2 },
      summaryMetrics: null, factsMetrics: [{ currentRequestTokens: 2, contextTokens: 4, responseTokens: 3 }],
      contextMetadata: { mode: 'STICKY_FACTS', recentMessageCount: 3, summary: null, summarizedMessageCount: 0,
        facts: { coveredUserMessageCount: 2, facts: { project: 'Helios' } } } });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('STICKY_FACTS');
    expect(fixture.nativeElement.textContent).toContain('Sticky facts maintenance');
    expect(fixture.nativeElement.textContent).toContain('Helios');
  });

  it('creates, lists and switches independent branches', () => {
    component.experiment = 'AGENT';
    component.createCheckpoint();
    const checkpoint = http.expectOne(`/api/dialogs/${dialog().id}/agent/checkpoints`);
    expect(checkpoint.request.body).toEqual({});
    checkpoint.flush({ id: 'checkpoint-1' });
    const checkpointSave = http.expectOne(r => r.method === 'PUT'); checkpointSave.flush(dialog());
    expect(component.checkpointId).toBe('checkpoint-1');

    component.createBranch();
    const branchRequest = http.expectOne(`/api/dialogs/${dialog().id}/agent/checkpoints/checkpoint-1/branches`);
    branchRequest.flush({ id: 'branch-a', checkpointId: 'checkpoint-1', history: [] });
    const branchSave = http.expectOne(r => r.method === 'PUT'); branchSave.flush(dialog());
    expect(component.branchId).toBe('branch-a');
    expect(component.branches()).toHaveLength(1);
    component.switchBranch(null);
    const switchSave = http.expectOne(r => r.method === 'PUT'); switchSave.flush(dialog());
    expect(component.branchId).toBeNull();
  });

  it('renders only the selected branch projection and restores the linear strategy', () => {
    const id = dialog().id;
    component.selectAgent();
    flushHealth();
    flushTopology(id, [
      { id: 'branch-a', checkpointId: 'checkpoint-a', history: [
        { role: 'system', content: 'instruction' }, { role: 'user', content: 'PostgreSQL' }, { role: 'assistant', content: 'answer A' }] },
      { id: 'branch-b', checkpointId: 'checkpoint-b', history: [
        { role: 'system', content: 'instruction' }, { role: 'user', content: 'ClickHouse' }, { role: 'assistant', content: 'answer B' }] },
    ], [
      { id: 'checkpoint-a', baseHistory: [{ role: 'system', content: 'instruction' }], branches: [] },
      { id: 'checkpoint-b', baseHistory: [{ role: 'system', content: 'instruction' }], branches: [] },
    ]);
    component.selectLinearContextMode('STICKY_FACTS');
    const strategySave = http.expectOne(r => r.method === 'PUT'); strategySave.flush(dialog());
    component.switchBranch('branch-a');
    const branchSave = http.expectOne(r => r.method === 'PUT'); branchSave.flush(dialog()); fixture.detectChanges();
    expect(component.contextMode).toBe('FULL');
    expect(fixture.nativeElement.textContent).toContain('PostgreSQL');
    expect(fixture.nativeElement.textContent).not.toContain('ClickHouse');
    component.switchBranch('branch-b');
    const siblingSave = http.expectOne(r => r.method === 'PUT'); siblingSave.flush(dialog()); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ClickHouse');
    expect(fixture.nativeElement.textContent).not.toContain('PostgreSQL');
    component.switchBranch(null);
    const linearSave = http.expectOne(r => r.method === 'PUT'); linearSave.flush(dialog());
    expect(component.contextMode).toBe('STICKY_FACTS');
  });

  it('keeps an explicit checkpoint selection through topology reload and creates from it', () => {
    const id = '22222222-2222-2222-2222-222222222222';
    component.openDialog(id);
    http.expectOne(`/api/dialogs/${id}`).flush(dialog(id, [], { experiment: 'AGENT', selectedMode: 'FREE',
      selectedStrategy: 'DIRECT', selectedTemperature: 0, branchId: 'branch-a', checkpointId: 'checkpoint-1' }));
    const topology = [
      { id: 'branch-a', checkpointId: 'checkpoint-1', history: [] },
    ];
    const checkpoints = [
      { id: 'checkpoint-1', baseHistory: [], branches: [] },
      { id: 'checkpoint-2', baseHistory: [], branches: [] },
    ];
    flushHealth(); flushTopology(id, topology, checkpoints);

    component.selectCheckpoint('checkpoint-2');
    http.expectOne(r => r.method === 'PUT').flush(dialog());
    component.selectAgent();
    flushHealth();
    flushTopology(id, topology, checkpoints);
    expect(component.checkpointId).toBe('checkpoint-2');

    component.createBranch();
    const request = http.expectOne(`/api/dialogs/${id}/agent/checkpoints/checkpoint-2/branches`);
    request.flush({ id: 'branch-c', checkpointId: 'checkpoint-2', history: [] });
    http.expectOne(r => r.method === 'PUT').flush(dialog());
  });

  it('renders completed maintenance metrics alongside an Agent error without main metrics', () => {
    component.experiment = 'AGENT'; component.input = 'facts failure'; component.analyze();
    http.expectOne(`/api/dialogs/${dialog().id}/agent/messages`).flush({ error: 'main failed', summaryMetrics: null,
      factsMetrics: [{ currentRequestTokens: 2, contextTokens: 4, responseTokens: 3, providerUsage: null }] },
      { status: 502, statusText: 'Bad Gateway' });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('main failed');
    expect(fixture.nativeElement.textContent).toContain('Sticky facts maintenance · calls: 1');
    expect(fixture.nativeElement.textContent).toContain('2 / 4 / 3');
    expect(fixture.nativeElement.textContent).not.toContain('Day 8/9 · локальная оценка');
  });

  it('renders summary maintenance metrics alongside an Agent error without main metrics', () => {
    component.experiment = 'AGENT'; component.input = 'summary failure'; component.analyze();
    http.expectOne(`/api/dialogs/${dialog().id}/agent/messages`).flush({ error: 'main overflow', factsMetrics: [],
      summaryMetrics: { currentRequestTokens: 5, contextTokens: 8, responseTokens: 2, providerUsage: null } },
      { status: 413, statusText: 'Payload Too Large' });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('main overflow');
    expect(fixture.nativeElement.textContent).toContain('Summary generation');
    expect(fixture.nativeElement.textContent).toContain('5 / 8 / 2');
    expect(fixture.nativeElement.textContent).not.toContain('Day 8/9 · локальная оценка');
  });

  it('blocks branch send during topology loading and shows the next completed response with metrics', () => {
    const id = dialog().id;
    component.experiment = 'AGENT'; component.branchId = 'branch-a'; component.input = 'branch turn';
    component.selectAgent(); fixture.detectChanges();
    flushHealth();
    const button = [...fixture.nativeElement.querySelectorAll('button')]
      .find((item: HTMLButtonElement) => item.textContent?.trim() === 'Проанализировать') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    component.analyze();
    http.expectNone(`/api/dialogs/${id}/agent/messages`);
    expect(component.input).toBe('branch turn');

    flushTopology(id, [{ id: 'branch-a', checkpointId: 'checkpoint-1', history: [] }],
      [{ id: 'checkpoint-1', baseHistory: [], branches: [] }]);
    fixture.detectChanges();
    expect(button.disabled).toBe(false);

    component.analyze();
    const request = http.expectOne(`/api/dialogs/${id}/agent/messages`);
    expect(request.request.body).toEqual({ input: 'branch turn', contextMode: 'FULL', recentMessageCount: 4, branchId: 'branch-a' });
    request.flush({ analysis: 'branch response', metrics: { currentRequestTokens: 1, contextTokens: 2, responseTokens: 3, providerUsage: null },
      summaryMetrics: null, factsMetrics: [], contextMetadata: { mode: 'FULL', recentMessageCount: 4, summary: null, summarizedMessageCount: 0 } });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('branch response');
    expect(fixture.nativeElement.textContent).toContain('1 / 2 / 3');
  });

  it('ignores stale topology after a dialog switch and renders a topology loading error', () => {
    const a = dialog().id;
    const b = '22222222-2222-2222-2222-222222222222';
    component.selectAgent();
    flushHealth();
    component.openDialog(b);
    http.expectOne(`/api/dialogs/${b}`).flush(dialog(b, [], { experiment: 'AGENT', selectedMode: 'FREE',
      selectedStrategy: 'DIRECT', selectedTemperature: 0 }));
    flushHealth();
    http.expectOne(`/api/dialogs/${a}/agent/branches`).flush([{ id: 'old-branch', checkpointId: 'old', history: [] }]);
    http.expectOne(`/api/dialogs/${a}/agent/checkpoints`).flush([{ id: 'old', baseHistory: [], branches: [] }]);
    flushTopology(b);
    expect(component.branchId).toBeNull();

    component.selectAgent();
    flushHealth();
    http.expectOne(`/api/dialogs/${b}/agent/branches`).flush({ error: 'unavailable' }, { status: 500, statusText: 'Server Error' });
    expect(component.topologyError()).toContain('Не удалось загрузить topology');
  });

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
