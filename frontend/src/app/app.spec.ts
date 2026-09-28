import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { App } from './app';

interface TestApp {
  input: string;
  experiment: 'FORMAT' | 'REASONING' | 'TEMPERATURE' | 'MODELS' | 'AGENT';
  selectedModelKey: string;
  selectedAgentModelKey: string | null;
  selectedProfileId: string | null;
  chooseProfile(id: string | null): void;
  profiles(): readonly { id: string; name: string; instructions: string; responseStyle: string; responseFormat: string }[];
  profilesError(): string;
  loadProfiles(): void;
  newProfile(): void;
  editProfile(profile: { id: string; name: string; instructions: string; responseStyle: string; responseFormat: string }): void;
  saveProfile(): void;
  profileDraft: { name: string; instructions: string; responseStyle: string; responseFormat: string };
  profileEditing: boolean;
  chooseAgentModel(key: string): void;
  editMemory(scope?: 'SHORT_TERM' | 'WORKING' | 'LONG_TERM', key?: string, value?: string): void;
  memoryEditing(): boolean;
  taskGoalDraft: string;
  taskCreationOpen: boolean;
  taskPlanDraft: string;
  taskStepDraft: string;
  executionResultDraft: string;
  validationEvidenceDraft: string;
  validationFailureReasonDraft: string;
  currentTask(): { id: string; state: { stage: string; status: string; revision: number } } | null;
  tasks(): readonly { id: string; state: { revision: number } }[];
  chooseTask(id: string | null): void;
  createTask(): void;
  toggleTaskCreation(): void;
  adoptLegacyScope(): void;
  applyTaskAction(action: string): void;
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
  appliedTaskId: string | null;
  memoryScope: 'SHORT_TERM' | 'WORKING' | 'LONG_TERM';
  memoryKey: string;
  memoryValue: string;
  memory(): { taskId: string | null; shortTerm: Record<string, string>; working: Record<string, string>; longTerm: Record<string, string> };
  memoryError(): string;
  saveMemory(): void;
  newInvariant(): void;
  editInvariant(invariant: { id: string; scope: 'USER' | 'TASK'; taskId: string | null; name: string; rule: string }): void;
  cancelInvariantEdit(): void;
  saveInvariant(): void;
  loadEffectiveInvariants(): void;
  invariantScope: 'USER' | 'TASK';
  invariantNameDraft: string;
  invariantRuleDraft: string;
  effectiveInvariants(): readonly { id: string; scope: string; name: string; rule: string }[];
  invariantsError(): string;
  invariantEditing: boolean;
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
  showMoreDialogs(): void;
  toggleDialogActions(id: string, event: Event): void;
  deleteDialog(id: string, title: string, event: Event): void;
  dialogs(): readonly { id: string; title: string; createdAt: string; updatedAt: string }[];
  visibleDialogs(): readonly { id: string; title: string; createdAt: string; updatedAt: string }[];
  currentDialogId(): string | null;
  toolMode: 'ordinary' | 'git' | 'monitor';
  exchanges(): readonly { temperatureConclusion?: unknown }[];
}

const now = '2026-09-03T10:00:00Z';
const dialog = (id = '11111111-1111-1111-1111-111111111111', exchanges: unknown[] = [], ui?: unknown) => ({
  id, title: 'Новый диалог', createdAt: now, updatedAt: now, state: { exchanges, ui: ui ?? {
    experiment: 'FORMAT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0,
  } },
});
const controlled = (rawResponse = '{"summary":"Резюме","findings":[],"recommendation":"Проверить"}') => ({
  review: { summary: 'Резюме', findings: [], recommendation: 'Проверить' }, rawResponse,
});
const profiles = ['Luna', 'Terra', 'Sol'].map((name, index) => ({
  key: ['WEAK', 'MEDIUM', 'STRONG'][index], label: `GPT-5.6 ${name}`, provider: 'OPENAI',
  modelId: `gpt-5.6-${name.toLowerCase()}`, modelUrl: `https://developers.openai.com/api/docs/models/gpt-5.6-${name.toLowerCase()}`,
}));
const agentProfiles = [
  { id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', name: 'Краткий инженер', instructions: 'Кратко.', responseStyle: 'technical', responseFormat: 'short' },
  { id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', name: 'Объясняющий инженер', instructions: 'Пошагово.', responseStyle: 'teaching', responseFormat: 'structured' },
];
const managedTask = (id = '33333333-3333-3333-3333-333333333333', revision = 0, stage = 'PLANNING', status = 'ACTIVE') => ({
  id, goal: 'Подготовить Java/PostgreSQL engineering solution', state: { stage, currentStep: 'Prepare and approve a plan', expectedAction: 'Approve the plan', status, revision },
  approvedPlan: '', executionResult: '', validationEvidence: '',
  allowedActions: status === 'PAUSED' ? ['RESUME'] : stage === 'PLANNING' ? ['APPROVE_PLAN', 'UPDATE_CURRENT_STEP', 'PAUSE']
    : stage === 'EXECUTION' ? ['START_VALIDATION', 'UPDATE_CURRENT_STEP', 'PAUSE']
    : stage === 'VALIDATION' ? ['ACCEPT_VALIDATION', 'VALIDATION_FAILED', 'UPDATE_CURRENT_STEP', 'PAUSE'] : [],
  createdAt: now, updatedAt: now,
});
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
    http.expectOne('/api/agent-model-options').flush([
      { key: 'DEEPSEEK', provider: 'DEEPSEEK', label: 'deepseek-v4-flash' },
      ...profiles.map(({ key, provider, label }) => ({ key, provider, label })),
    ]);
    http.expectOne('/api/profiles').flush(agentProfiles);
    http.expectOne('/api/tasks').flush([]);
    http.expectOne('/api/dialogs').flush([]);
    http.expectOne('/api/dialogs').flush(dialog());
    fixture.detectChanges();
  });

  afterEach(() => {
    http.match('/api/invariants/effective').forEach(request => request.flush([]));
    http.match(request => request.url.startsWith('/api/invariants/effective?')).forEach(request => request.flush([]));
    http.verify({ ignoreCancelled: true });
  });

  function flushSave(): void {
    http.expectOne(request => request.method === 'PUT' && /^\/api\/dialogs\/[^/]+$/.test(request.url)).flush(dialog());
  }
  function flushTopology(id = dialog().id, branches: unknown[] = [], checkpoints: unknown[] = []): void {
    http.expectOne(`/api/dialogs/${id}/agent/branches`).flush(branches);
    http.expectOne(`/api/dialogs/${id}/agent/checkpoints`).flush(checkpoints);
  }
  function flushHealth(): void { http.expectOne('/api/health').flush({ status: 'UP' }); }
  function flushMemory(id = dialog().id, taskId: string | null = null,
    snapshot = { shortTerm: {}, working: {}, longTerm: {} }): void {
    const query = taskId ? `?taskId=${taskId}` : '';
    http.expectOne(`/api/dialogs/${id}/agent/memory${query}`).flush({ taskId, ...snapshot });
  }
  function selectTask(task: ReturnType<typeof managedTask>): void {
    component.chooseTask(task.id);
    const selection = http.expectOne(`/api/dialogs/${component.currentDialogId()}/task-selection`);
    expect(selection.request.body).toEqual({ taskId: task.id }); selection.flush(dialog(component.currentDialogId()!));
  }

  it('opens MCP workspace without discovery and returns to the existing chat controls', () => {
    const workspace = [...fixture.nativeElement.querySelectorAll('.primary-nav button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Рабочая область')) as HTMLButtonElement;
    workspace.click(); fixture.detectChanges();
    http.expectOne('/api/mcp/connections').flush([{ id: 'idea', name: 'IntelliJ IDEA MCP', configured: true }]);
    http.expectNone('/api/mcp/connections/idea/discovery');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Инструменты MCP');
    expect(fixture.nativeElement.querySelector('.composer')).toBeNull();

    (fixture.nativeElement.querySelector('.primary-nav button') as HTMLButtonElement).click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.composer')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-agent-inspector')).not.toBeNull();
  });

  it('opens Monitor in Workspace and prepares explicit chat read without auto-sending', () => {
    const workspace = [...fixture.nativeElement.querySelectorAll('.primary-nav button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Рабочая область')) as HTMLButtonElement;
    workspace.click(); fixture.detectChanges();
    http.expectOne('/api/mcp/connections').flush([{ id: 'idea', name: 'IntelliJ IDEA MCP', configured: true }]);
    const monitorTab = [...fixture.nativeElement.querySelectorAll('.workspace-nav button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Монитор')) as HTMLButtonElement;
    monitorTab.click(); fixture.detectChanges();
    http.expectOne('/api/repository-monitor/configuration').flush({ minimumIntervalSeconds: 60,
      maximumIntervalSeconds: 86400, defaultIntervalSeconds: 300 });
    http.expectOne('/api/repository-monitor').flush({ monitorId: 'fixture', repositoryRef: 'fixture',
      enabled: false, intervalSeconds: null, configRevision: 0, nextRunAt: null,
      aggregate: { successCount: 0, failureCount: 0, dirtySampleCount: 0, headTransitionCount: 0,
        branchTransitionCount: 0, firstSuccessAt: null, lastSuccessAt: null, lastCompletedAt: null,
        lastOutcome: null, lastFailureCode: null, latestStatus: null }, latestDigest: null,
      lastCommand: null, health: { status: 'IDLE', code: null } });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Монитор остановлен');
    (fixture.nativeElement.querySelector('.observations button') as HTMLButtonElement).click(); fixture.detectChanges();
    flushHealth(); flushTopology(); flushMemory();
    expect(component.toolMode).toBe('monitor');
    expect(component.input).toContain('сводку монитора');
    http.expectNone(`/api/dialogs/${dialog().id}/agent/messages`);
  });

  it('sends Git mode once, shows factual result apart from the answer, and then sends ordinary mode', () => {
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory(); fixture.detectChanges();
    const mode = fixture.nativeElement.querySelector('select[aria-label="Режим инструмента"]') as HTMLSelectElement;
    mode.value = 'git'; mode.dispatchEvent(new Event('change')); fixture.detectChanges();
    expect(mode.value).toBe('git');
    component.input = 'Покажи Git статус'; component.analyze();
    const selected = http.expectOne(`/api/dialogs/${dialog().id}/agent/messages`);
    expect(selected.request.body.requireGitStatusTool).toBe(true);
    selected.flush({ analysis: 'Ответ модели', toolTrace: { turnId: 'turn-1', toolRequested: true,
      toolStatus: 'SUCCESS', turnStatus: 'SUCCESS', code: null, toolResult: {
        repositoryRef: 'workspace', observedAt: now, headState: 'ATTACHED', branch: 'fixture', head: 'abc123', dirty: true,
        changeCounts: { staged: 0, unstaged: 1, untracked: 0, conflicted: 0, submoduleChanged: 0 },
      } } }); flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ответ модели');
    expect(fixture.nativeElement.querySelector('.git-tool-trace')?.textContent).toContain('fixture');
    expect(component.toolMode).toBe('ordinary');
    component.input = 'Обычный вопрос'; component.analyze();
    const ordinary = http.expectOne(`/api/dialogs/${dialog().id}/agent/messages`);
    expect(ordinary.request.body).not.toHaveProperty('requireGitStatusTool');
    ordinary.flush({ analysis: 'Обычный ответ' }); flushSave();
  });

  it('clears Git selection on Dialog switch and rejects an unsupported model before a tool request', () => {
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory();
    component.toolMode = 'git';
    const nextId = '22222222-2222-2222-2222-222222222222';
    component.openDialog(nextId);
    http.expectOne(`/api/dialogs/${nextId}`).flush(dialog(nextId, [], { experiment: 'AGENT' }));
    flushHealth(); flushTopology(nextId); flushMemory(nextId);
    expect(component.toolMode).toBe('ordinary');
    component.chooseAgentModel('MEDIUM'); flushSave();
    component.toolMode = 'git'; component.input = 'Git статус'; component.analyze();
    http.expectNone(`/api/dialogs/${nextId}/agent/messages`);
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('не поддерживает вызов инструмента');
    expect(component.toolMode).toBe('ordinary');
  });

  it('UX shell keeps the composer free of settings and reveals memory only on request', () => {
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory(); fixture.detectChanges();
    expect(document.title).toBe('Local AI Worker');
    expect(fixture.nativeElement.textContent).not.toContain('Engineering Review Mentor');
    expect(fixture.nativeElement.textContent).not.toContain('AI Advent · День 10');
    expect(fixture.nativeElement.querySelector('.composer .experiment-selector')).toBeNull();
    expect(fixture.nativeElement.querySelector('.inspector .experiment-selector')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.memory-editor')).toBeNull();
    expect(fixture.nativeElement.querySelector('.task-scope')).toBeNull();
    const add = fixture.nativeElement.querySelector('[aria-label="Добавить запись памяти"]') as HTMLButtonElement;
    add.click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.memory-editor')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.memory-layers').textContent).toContain('Краткосрочная');
  });

  it('UX shell sends only the backend selection key and restores selection per dialog', () => {
    const id = dialog().id;
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory(); fixture.detectChanges();
    const select = fixture.nativeElement.querySelector('[aria-label="Провайдер API и модель агента"]') as HTMLSelectElement;
    expect([...select.options].map(option => option.value)).toEqual(['DEEPSEEK', 'WEAK', 'MEDIUM', 'STRONG']);
    select.value = 'MEDIUM'; select.dispatchEvent(new Event('change')); fixture.detectChanges();
    const saved = http.expectOne(r => r.method === 'PUT');
    const ui = structuredClone(saved.request.body.state.ui); saved.flush(dialog());
    expect(ui.selectedAgentModelKey).toBe('MEDIUM');
    component.input = 'selected model'; component.analyze();
    const request = http.expectOne(`/api/dialogs/${id}/agent/messages`);
    expect(request.request.body).toEqual({ input: 'selected model', contextMode: 'FULL', recentMessageCount: 4, agentModelKey: 'MEDIUM' });
    request.flush({ analysis: 'OpenAI response' }); flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.mentor-message').textContent).toContain('GPT-5.6 Terra');
    component.chooseAgentModel('arbitrary-id'); http.expectNone(r => r.method === 'PUT');
    const b = '22222222-2222-2222-2222-222222222222';
    component.openDialog(b); http.expectOne(`/api/dialogs/${b}`).flush(dialog(b));
    expect(component.selectedAgentModelKey).toBeNull();
    component.openDialog(id); http.expectOne(`/api/dialogs/${id}`).flush(dialog(id, [], ui));
    flushHealth(); flushTopology(); flushMemory();
    expect(component.selectedAgentModelKey).toBe('MEDIUM');
  });

  it('loads profiles, renders the no-profile option and persists the selected profile per dialog', () => {
    const a = dialog().id;
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory(); fixture.detectChanges();
    const selector = fixture.nativeElement.querySelector('[aria-label="Профиль агента"]') as HTMLSelectElement;
    expect([...selector.options].map(option => option.text)).toEqual(['Без профиля', 'Краткий инженер', 'Объясняющий инженер']);
    selector.value = agentProfiles[0].id; selector.dispatchEvent(new Event('change'));
    const saved = http.expectOne(`/api/dialogs/${a}/profile-selection`);
    expect(saved.request.body).toEqual({ profileId: agentProfiles[0].id });
    saved.flush(dialog(a, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: agentProfiles[0].id,
    }));

    const b = '22222222-2222-2222-2222-222222222222';
    component.openDialog(b); http.expectOne(`/api/dialogs/${b}`).flush(dialog(b));
    expect(component.selectedProfileId).toBeNull();
    component.openDialog(a); http.expectOne(`/api/dialogs/${a}`).flush(dialog(a, [], { experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: agentProfiles[0].id }));
    flushHealth(); flushTopology(a); flushMemory(a);
    expect(component.selectedProfileId).toBe(agentProfiles[0].id);
  });

  it('restores a saved profile after page state reload and falls back for an unresolved profile', () => {
    const id = '22222222-2222-2222-2222-222222222222';
    component.openDialog(id); http.expectOne(`/api/dialogs/${id}`).flush(dialog(id, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: agentProfiles[1].id,
    }));
    flushHealth(); flushTopology(id); flushMemory(id);
    expect(component.selectedProfileId).toBe(agentProfiles[1].id);

    const missing = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
    const unresolvedId = '33333333-3333-3333-3333-333333333333';
    component.openDialog(unresolvedId);
    http.expectOne(`/api/dialogs/${unresolvedId}`).flush(dialog(unresolvedId, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: missing,
    }));
    flushHealth();
    flushTopology(unresolvedId); flushMemory(unresolvedId);
    expect(component.selectedProfileId).toBeNull();
  });

  it('clears a stale saved profile ID restored after an empty catalog and omits it from the next request', () => {
    const unknown = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
    component.loadProfiles(); http.expectOne('/api/profiles').flush([]);
    const staleDialogId = '33333333-3333-3333-3333-333333333333';
    component.openDialog(staleDialogId);
    http.expectOne(`/api/dialogs/${staleDialogId}`).flush(dialog(staleDialogId, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: unknown,
    }));
    flushHealth(); flushTopology(staleDialogId); flushMemory(staleDialogId);
    expect(component.selectedProfileId).toBeNull();

    component.input = 'without stale profile'; component.analyze();
    const request = http.expectOne(`/api/dialogs/${staleDialogId}/agent/messages`);
    expect(request.request.body).not.toHaveProperty('profileId');
    request.flush({ analysis: 'answer' }); flushSave();
  });

  it('keeps a restored profile ID while the catalog is still loading', () => {
    fixture.destroy();
    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance as unknown as TestApp;
    fixture.detectChanges();
    http.expectOne('/api/agent-model-options').flush([
      { key: 'DEEPSEEK', provider: 'DEEPSEEK', label: 'deepseek-v4-flash' },
      ...profiles.map(({ key, provider, label }) => ({ key, provider, label })),
    ]);
    const pendingCatalog = http.expectOne('/api/profiles');
    http.expectOne('/api/tasks').flush([]);
    http.expectOne('/api/dialogs').flush([]);
    http.expectOne('/api/dialogs').flush(dialog());
    const unknown = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
    const restoredId = '33333333-3333-3333-3333-333333333333';
    component.openDialog(restoredId);
    http.expectOne(`/api/dialogs/${restoredId}`).flush(dialog(restoredId, [], {
      experiment: 'FORMAT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: unknown,
    }));
    expect(component.selectedProfileId).toBe(unknown);
    pendingCatalog.flush([]);
    expect(component.selectedProfileId).toBeNull();
  });

  it('persists existing and cleared Profile selection only through the narrow endpoint', () => {
    const id = dialog().id;
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory();
    component.chooseProfile(agentProfiles[0].id);
    const select = http.expectOne(`/api/dialogs/${id}/profile-selection`);
    expect(select.request.method).toBe('PUT'); expect(select.request.body).toEqual({ profileId: agentProfiles[0].id });
    http.expectNone(request => request.method === 'PUT' && request.url === `/api/dialogs/${id}`);
    select.flush(dialog(id, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0,
      selectedProfileId: agentProfiles[0].id,
    }));
    expect(component.selectedProfileId).toBe(agentProfiles[0].id);
    component.input = 'with profile'; component.analyze();
    const selected = http.expectOne(`/api/dialogs/${id}/agent/messages`);
    expect(selected.request.body.profileId).toBe(agentProfiles[0].id);
    selected.flush({ analysis: 'answer' }); flushSave();

    component.chooseProfile(null);
    const clear = http.expectOne(`/api/dialogs/${id}/profile-selection`);
    expect(clear.request.method).toBe('PUT'); expect(clear.request.body).toEqual({ profileId: null });
    http.expectNone(request => request.method === 'PUT' && request.url === `/api/dialogs/${id}`);
    clear.flush(dialog(id, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0,
      selectedProfileId: null,
    }));
    expect(component.selectedProfileId).toBeNull();
    component.input = 'without profile'; component.analyze();
    const noProfile = http.expectOne(`/api/dialogs/${id}/agent/messages`);
    expect(noProfile.request.body).not.toHaveProperty('profileId');
    noProfile.flush({ analysis: 'answer' }); flushSave();
  });

  it('uses an optimistically selected Profile while its narrow persistence request is pending', () => {
    const id = dialog().id;
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory();
    component.chooseProfile(agentProfiles[0].id);
    http.expectOne(`/api/dialogs/${id}/profile-selection`).flush(dialog(id, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: agentProfiles[0].id,
    }));

    component.chooseProfile(agentProfiles[1].id);
    const pending = http.expectOne(`/api/dialogs/${id}/profile-selection`);
    expect(component.selectedProfileId).toBe(agentProfiles[1].id);
    component.input = 'use the new profile'; component.analyze();
    const message = http.expectOne(`/api/dialogs/${id}/agent/messages`);
    expect(message.request.body.profileId).toBe(agentProfiles[1].id);
    message.flush({ analysis: 'answer' }); flushSave();
    pending.flush(dialog(id, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: agentProfiles[1].id,
    }));
  });

  it('omits Profile while clearing its narrow persistence request is pending', () => {
    const id = dialog().id;
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory();
    component.chooseProfile(agentProfiles[0].id);
    http.expectOne(`/api/dialogs/${id}/profile-selection`).flush(dialog(id, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: agentProfiles[0].id,
    }));

    component.chooseProfile(null);
    const pending = http.expectOne(`/api/dialogs/${id}/profile-selection`);
    expect(component.selectedProfileId).toBeNull();
    component.input = 'use no profile'; component.analyze();
    const message = http.expectOne(`/api/dialogs/${id}/agent/messages`);
    expect(message.request.body).not.toHaveProperty('profileId');
    message.flush({ analysis: 'answer' }); flushSave();
    pending.flush(dialog(id, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: null,
    }));
  });

  it('serializes rapid Profile choices and keeps the latest choice across a Dialog switch', () => {
    const id = dialog().id;
    const otherId = '22222222-2222-2222-2222-222222222222';
    component.chooseProfile(agentProfiles[0].id);
    const first = http.expectOne(`/api/dialogs/${id}/profile-selection`);
    component.chooseProfile(agentProfiles[1].id);
    expect(component.selectedProfileId).toBe(agentProfiles[1].id);
    http.expectNone(`/api/dialogs/${id}/profile-selection`);

    component.openDialog(otherId); http.expectOne(`/api/dialogs/${otherId}`).flush(dialog(otherId));
    component.openDialog(id); http.expectOne(`/api/dialogs/${id}`).flush(dialog(id));
    expect(component.selectedProfileId).toBe(agentProfiles[1].id);

    first.flush(dialog(id, [], { selectedProfileId: agentProfiles[0].id }));
    expect(component.selectedProfileId).toBe(agentProfiles[1].id);
    const latest = http.expectOne(`/api/dialogs/${id}/profile-selection`);
    expect(latest.request.body).toEqual({ profileId: agentProfiles[1].id });
    latest.flush(dialog(id, [], { selectedProfileId: agentProfiles[1].id }));
    expect(component.selectedProfileId).toBe(agentProfiles[1].id);
  });

  it('persists the latest Profile choice when an earlier queued request fails', () => {
    const id = dialog().id;
    component.chooseProfile(agentProfiles[0].id);
    const first = http.expectOne(`/api/dialogs/${id}/profile-selection`);
    component.chooseProfile(agentProfiles[1].id);
    first.flush({ error: 'failed' }, { status: 500, statusText: 'Server Error' });

    http.expectNone(`/api/dialogs/${id}`);
    const latest = http.expectOne(`/api/dialogs/${id}/profile-selection`);
    expect(latest.request.body).toEqual({ profileId: agentProfiles[1].id });
    latest.flush(dialog(id, [], { selectedProfileId: agentProfiles[1].id }));
    expect(component.selectedProfileId).toBe(agentProfiles[1].id);
  });

  it('ignores a stale Dialog GET after Profile persistence succeeds', () => {
    const id = dialog().id;
    const otherId = '22222222-2222-2222-2222-222222222222';
    component.chooseProfile(agentProfiles[0].id);
    http.expectOne(`/api/dialogs/${id}/profile-selection`).flush(dialog(id, [], {
      selectedProfileId: agentProfiles[0].id,
    }));
    component.chooseProfile(agentProfiles[1].id);
    const selection = http.expectOne(`/api/dialogs/${id}/profile-selection`);
    component.openDialog(otherId); http.expectOne(`/api/dialogs/${otherId}`).flush(dialog(otherId));
    component.openDialog(id);
    const staleDialog = http.expectOne(`/api/dialogs/${id}`);

    selection.flush(dialog(id, [], { selectedProfileId: agentProfiles[1].id }));
    staleDialog.flush(dialog(id, [], { selectedProfileId: agentProfiles[0].id }));
    expect(component.selectedProfileId).toBe(agentProfiles[1].id);
  });

  it('reloads the confirmed Profile after a failed optimistic selection', () => {
    const id = dialog().id;
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory();
    component.chooseProfile(agentProfiles[0].id);
    http.expectOne(`/api/dialogs/${id}/profile-selection`).flush(dialog(id, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: agentProfiles[0].id,
    }));

    component.chooseProfile(agentProfiles[1].id);
    const failed = http.expectOne(`/api/dialogs/${id}/profile-selection`);
    expect(component.selectedProfileId).toBe(agentProfiles[1].id);
    failed.flush({ error: 'Сохранение Profile не удалось.' }, { status: 500, statusText: 'Server Error' });
    http.expectOne(`/api/dialogs/${id}`).flush(dialog(id, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, selectedProfileId: agentProfiles[0].id,
    }));
    expect(component.selectedProfileId).toBe(agentProfiles[0].id);
    expect(component.profilesError()).toBe('Сохранение Profile не удалось.');
  });

  it('creates and edits a profile in the inspector', () => {
    component.newProfile();
    component.profileDraft = { name: 'Новый профиль', instructions: 'Точно.', responseStyle: 'technical', responseFormat: 'short' };
    component.saveProfile();
    const create = http.expectOne('/api/profiles');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(component.profileDraft);
    const created = { ...component.profileDraft, id: 'cccccccc-cccc-cccc-cccc-cccccccccccc' };
    create.flush(created);
    const persisted = http.expectOne(`/api/dialogs/${dialog().id}/profile-selection`);
    expect(persisted.request.body).toEqual({ profileId: created.id }); persisted.flush(dialog());

    component.editProfile(created); component.profileDraft.responseFormat = 'structured'; component.saveProfile();
    const update = http.expectOne(`/api/profiles/${created.id}`);
    expect(update.request.method).toBe('PUT'); expect(update.request.body.responseFormat).toBe('structured');
    update.flush({ ...created, responseFormat: 'structured' }); flushSave();
  });

  it('preserves newer origin-dialog UI state while applying a created profile', () => {
    const a = dialog().id;
    component.newProfile();
    component.profileDraft = { name: 'Новый профиль', instructions: 'Точно.', responseStyle: 'technical', responseFormat: 'short' };
    component.saveProfile();
    const create = http.expectOne('/api/profiles');
    component.chooseAgentModel('MEDIUM');
    const newerState = http.expectOne(`/api/dialogs/${a}`);
    newerState.flush(dialog(a, [], {
      experiment: 'FORMAT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0,
      selectedAgentModelKey: 'MEDIUM', appliedTaskId: '33333333-3333-3333-3333-333333333333',
    }));

    const created = { ...component.profileDraft, id: 'cccccccc-cccc-cccc-cccc-cccccccccccc' };
    create.flush(created);
    expect(component.profiles()).toContainEqual(created);
    const selection = http.expectOne(`/api/dialogs/${a}/profile-selection`);
    expect(selection.request.body).toEqual({ profileId: created.id });
    selection.flush(dialog(a, [], {
      experiment: 'FORMAT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0,
      selectedAgentModelKey: 'MEDIUM', appliedTaskId: '33333333-3333-3333-3333-333333333333', selectedProfileId: created.id,
    }));
    expect(component.selectedAgentModelKey).toBe('MEDIUM');
    expect(component.selectedProfileId).toBe(created.id);
  });

  it('keeps a newer editor in dialog B when dialog A profile creation resolves', () => {
    const a = dialog().id;
    const b = '22222222-2222-2222-2222-222222222222';
    component.newProfile();
    component.profileDraft = { name: 'Профиль A', instructions: 'A.', responseStyle: 'technical', responseFormat: 'short' };
    component.saveProfile();
    const create = http.expectOne('/api/profiles');
    component.openDialog(b); http.expectOne(`/api/dialogs/${b}`).flush(dialog(b));
    component.newProfile();
    component.profileDraft = { name: 'Черновик B', instructions: 'B.', responseStyle: 'teaching', responseFormat: 'structured' };

    const created = { id: 'cccccccc-cccc-cccc-cccc-cccccccccccc', name: 'Профиль A', instructions: 'A.', responseStyle: 'technical', responseFormat: 'short' };
    create.flush(created);
    expect(component.currentDialogId()).toBe(b);
    expect(component.profileEditing).toBe(true);
    expect(component.profileDraft).toEqual({ name: 'Черновик B', instructions: 'B.', responseStyle: 'teaching', responseFormat: 'structured' });
    const selection = http.expectOne(`/api/dialogs/${a}/profile-selection`);
    expect(selection.request.body).toEqual({ profileId: created.id });
    selection.flush(dialog(a));
  });

  it('closes the memory editor and clears displayed memory when managed Task or dialog changes', () => {
    component.selectAgent(); flushHealth(); flushTopology();
    flushMemory(dialog().id, null, { shortTerm: { old: 'previous' }, working: {}, longTerm: {} });
    component.editMemory('WORKING', 'draft', 'old task');
    const task = managedTask(); (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([task]);
    selectTask(task);
    expect(component.memoryEditing()).toBe(false);
    expect(component.memoryKey).toBe(''); expect(component.memory().shortTerm).toEqual({});
    flushMemory(dialog().id, task.id);
    component.editMemory('SHORT_TERM', 'draft', 'old dialog'); component.input = 'old composer';
    const b = '22222222-2222-2222-2222-222222222222';
    component.openDialog(b); http.expectOne(`/api/dialogs/${b}`).flush(dialog(b));
    expect(component.memoryEditing()).toBe(false); expect(component.memoryValue).toBe('');
    expect(component.input).toBe(''); expect(component.appliedTaskId).toBeNull();
  });

  it('UX shell defaults new agent dialogs to the catalog default without moving old dialogs to OpenAI', async () => {
    component.newDialog();
    const b = '22222222-2222-2222-2222-222222222222';
    http.expectOne('/api/dialogs').flush({ ...dialog(b), state: {} });
    flushHealth(); flushTopology(b); flushMemory(b); fixture.detectChanges();
    await fixture.whenStable(); fixture.detectChanges();
    expect(component.experiment).toBe('AGENT'); expect(component.selectedAgentModelKey).toBeNull();
    const select = fixture.nativeElement.querySelector('[aria-label="Провайдер API и модель агента"]') as HTMLSelectElement;
    expect(select.value).toBe('DEEPSEEK');
  });

  it('shows fifteen recent dialogs and reveals the next batch on demand', () => {
    const dialogs = Array.from({ length: 17 }, (_, index) => ({ ...dialog(`${String(index + 2).padStart(8, '0')}-1111-1111-1111-111111111111`), title: `Dialog ${index + 1}` }));
    (component as unknown as { dialogs: { set(items: unknown[]): void } }).dialogs.set(dialogs);
    fixture.detectChanges();

    expect(component.visibleDialogs()).toHaveLength(15);
    expect(fixture.nativeElement.textContent).toContain('Показать ещё');
    component.showMoreDialogs(); fixture.detectChanges();
    expect(component.visibleDialogs()).toHaveLength(17);
  });

  it('shows the overflow action and keeps the dialog when deletion is cancelled', () => {
    const id = dialog().id;
    component.toggleDialogActions(id, new Event('click')); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dialog-delete')).not.toBeNull();
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    component.deleteDialog(id, 'Новый диалог', new Event('click'));

    http.expectNone(request => request.method === 'DELETE');
    expect(component.dialogs()).toHaveLength(1);
  });

  it('deletes a selected dialog and opens the next remaining dialog', () => {
    const first = dialog().id;
    const second = '22222222-1111-1111-1111-111111111111';
    (component as unknown as { dialogs: { set(items: unknown[]): void } }).dialogs.set([
      { id: first, title: 'First', createdAt: now, updatedAt: now },
      { id: second, title: 'Second', createdAt: now, updatedAt: now },
    ]);
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.deleteDialog(first, 'First', new Event('click'));
    http.expectOne({ method: 'DELETE', url: `/api/dialogs/${first}` }).flush(null);
    http.expectOne(`/api/dialogs/${second}`).flush(dialog(second));

    expect(component.dialogs().map(item => item.id)).toEqual([second]);
    expect(component.currentDialogId()).toBe(second);
  });

  it('creates one empty dialog after deleting the last remaining dialog', () => {
    const id = dialog().id;
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.deleteDialog(id, 'Новый диалог', new Event('click'));
    http.expectOne({ method: 'DELETE', url: `/api/dialogs/${id}` }).flush(null);
    const replacement = '33333333-1111-1111-1111-111111111111';
    http.expectOne('/api/dialogs').flush(dialog(replacement));

    expect(component.currentDialogId()).toBe(replacement);
    expect(component.dialogs().map(item => item.id)).toEqual([replacement]);
  });

  it('sends Agent follow-ups by dialog, restores the UI archive and isolates A/B/A', () => {
    const a = dialog().id;
    const b = '22222222-2222-2222-2222-222222222222';
    const agentButton = [...fixture.nativeElement.querySelectorAll('.experiment-selector button')]
      .find((button: HTMLButtonElement) => button.textContent?.trim() === 'Агент') as HTMLButtonElement;
    agentButton.click(); fixture.detectChanges();
    flushHealth();
    flushTopology(a);
    flushMemory(a);
    expect(component.experiment).toBe('AGENT');
    expect(fixture.nativeElement.querySelector('.memory-panel')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.temperature-selector')).toBeNull();
    expect(fixture.nativeElement.querySelector('.action-buttons').textContent).not.toContain('Сравнить');
    expect(fixture.nativeElement.textContent).toContain('восстанавливает его после перезапуска');

    component.input = 'fact A'; component.analyze(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('mat-progress-bar')).not.toBeNull();
    component.analyze();
    const first = http.expectOne(`/api/dialogs/${a}/agent/messages`);
    expect(first.request.body).toEqual({ input: 'fact A', contextMode: 'FULL', recentMessageCount: 4, agentModelKey: 'DEEPSEEK' });
    first.flush({ analysis: '## Запомнил\n<img src=x onerror=alert(1)>', metrics: {
      currentRequestTokens: 2, contextTokens: 12, responseTokens: 5,
      providerUsage: { promptTokens: 20, completionTokens: 6, totalTokens: 26 },
    }, summaryMetrics: null, contextMetadata: { mode: 'FULL', recentMessageCount: 4, summary: null, summarizedMessageCount: 0 } });
    const savedFirst = http.expectOne(r => r.method === 'PUT');
    savedFirst.flush(dialog(a, savedFirst.request.body.state.exchanges, savedFirst.request.body.state.ui));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.analysis-text h2')?.textContent).toBe('Запомнил');
    expect(fixture.nativeElement.querySelector('.analysis-text img')).toBeNull();
    const details = fixture.nativeElement.querySelector('.technical-details') as HTMLDetailsElement;
    expect(details.open).toBe(false);
    expect(details.textContent).toContain('Локальная оценка токенов');
    expect(details.textContent).toContain('2 / 12 / 5');
    expect(details.textContent).toContain('20 / 6 / 26');

    component.input = 'follow-up A'; component.analyze();
    const followUp = http.expectOne(`/api/dialogs/${a}/agent/messages`);
    expect(followUp.request.body).toEqual({ input: 'follow-up A', contextMode: 'FULL', recentMessageCount: 4, agentModelKey: 'DEEPSEEK' });
    followUp.flush({ analysis: 'answer A' });
    const save = http.expectOne(r => r.method === 'PUT');
    const archive = structuredClone(save.request.body.state);
    expect(archive.ui.experiment).toBe('AGENT');
    expect(archive.exchanges.map((entry: { mode: string }) => entry.mode)).toEqual(['AGENT', 'AGENT']);
    save.flush(dialog(a, archive.exchanges, archive.ui));

    component.newDialog(); http.expectOne('/api/dialogs').flush(dialog(b));
    component.experiment = 'AGENT'; component.input = 'question B'; component.analyze();
    const requestB = http.expectOne(`/api/dialogs/${b}/agent/messages`);
    expect(requestB.request.body).toEqual({ input: 'question B', contextMode: 'FULL', recentMessageCount: 4, agentModelKey: 'DEEPSEEK' });
    requestB.flush({ analysis: 'no context B' });
    const saveB = http.expectOne(r => r.method === 'PUT');
    expect(saveB.request.body.state.exchanges).toHaveLength(1);
    saveB.flush(dialog(b, saveB.request.body.state.exchanges, saveB.request.body.state.ui));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('fact A');

    component.openDialog(a); http.expectOne(`/api/dialogs/${a}`).flush(dialog(a, archive.exchanges, archive.ui));
    flushHealth();
    flushTopology(a);
    flushMemory(a);
    fixture.detectChanges();
    expect(component.experiment).toBe('AGENT');
    expect(fixture.nativeElement.textContent).toContain('fact A');
    expect(fixture.nativeElement.textContent).not.toContain('question B');
    component.input = 'back to A'; component.analyze();
    const returnA = http.expectOne(`/api/dialogs/${a}/agent/messages`);
    expect(returnA.request.body).toEqual({ input: 'back to A', contextMode: 'FULL', recentMessageCount: 4, agentModelKey: 'DEEPSEEK' });
    returnA.flush({ analysis: 'still A' }); flushSave();
  });

  it('loads all memory layers, blocks WORKING without a task and reloads after an upsert', () => {
    const id = dialog().id;
    component.selectAgent(); flushHealth(); flushTopology(id);
    flushMemory(id, null, { shortTerm: { codeword: 'SATURN' }, working: {}, longTerm: { language: 'Java' } });
    fixture.detectChanges();
    expect(component.memory()).toEqual({ taskId: null, shortTerm: { codeword: 'SATURN' }, working: {}, longTerm: { language: 'Java' } });
    expect(fixture.nativeElement.querySelector('.memory-layers')?.textContent).toContain('SATURN');
    expect(fixture.nativeElement.querySelector('.memory-layers')?.textContent).toContain('Java');

    component.editMemory('WORKING', 'database', 'PostgreSQL');
    fixture.detectChanges();
    const saveButton = [...fixture.nativeElement.querySelectorAll('.memory-editor button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Сохранить')) as HTMLButtonElement;
    expect(saveButton.disabled).toBe(true);
    component.saveMemory(); http.expectNone(`/api/dialogs/${id}/agent/memory/WORKING`);

    component.memoryScope = 'SHORT_TERM'; component.saveMemory();
    const put = http.expectOne(`/api/dialogs/${id}/agent/memory/SHORT_TERM`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ taskId: null, key: 'database', value: 'PostgreSQL' });
    put.flush({ taskId: null, shortTerm: { database: 'PostgreSQL' }, working: {}, longTerm: {} });
    flushMemory(id, null, { shortTerm: { codeword: 'SATURN', database: 'PostgreSQL' }, working: {}, longTerm: { language: 'Java' } });
    expect(component.memory().shortTerm['database']).toBe('PostgreSQL');
  });

  it('selects one managed Task through the narrow endpoint and reuses it for memory and Agent requests across dialogs', () => {
    const a = dialog().id;
    const b = '22222222-2222-2222-2222-222222222222';
    component.selectAgent(); flushHealth(); flushTopology(a); flushMemory(a);
    const task = managedTask(); (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([task]);
    selectTask(task); const taskId = task.id;
    flushMemory(a, taskId, { shortTerm: {}, working: { database: 'PostgreSQL' }, longTerm: {} });
    expect(component.appliedTaskId).toBe(taskId);

    component.input = 'Which database?'; component.analyze();
    const message = http.expectOne(`/api/dialogs/${a}/agent/messages`);
    expect(message.request.body.taskId).toBe(taskId);
    message.flush({ analysis: 'PostgreSQL', contextMetadata: { mode: 'FULL', recentMessageCount: 4,
      summary: null, summarizedMessageCount: 0, memoryUsed: [{ scope: 'WORKING', keys: ['database'], entryCount: 1 }] } });
    flushSave();

    component.openDialog(b);
    http.expectOne(`/api/dialogs/${b}`).flush(dialog(b, [], { experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT',
      selectedTemperature: 0, appliedTaskId: taskId }));
    http.expectOne(`/api/tasks/${taskId}`).flush(task); flushHealth(); flushTopology(b);
    flushMemory(b, taskId, { shortTerm: {}, working: { database: 'PostgreSQL' }, longTerm: {} });
    expect(component.appliedTaskId).toBe(taskId);
    expect(component.currentTask()?.id).toBe(taskId);
    expect(component.memory().working['database']).toBe('PostgreSQL');
  });

  it('ignores stale memory after a task change and archives only value-free response evidence', () => {
    const id = dialog().id;
    const firstTask = '33333333-3333-4333-8333-333333333333';
    const secondTask = '44444444-4444-4444-8444-444444444444';
    component.selectAgent(); flushHealth(); flushTopology(id); flushMemory(id);

    const first = managedTask(firstTask); const second = managedTask(secondTask);
    (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([first, second]);
    selectTask(first); selectTask(second);
    flushMemory(id, secondTask, { shortTerm: {}, working: { current: 'SECOND_VALUE' }, longTerm: {} });
    flushMemory(id, firstTask, { shortTerm: {}, working: { stale: 'FIRST_VALUE' }, longTerm: {} });
    expect(component.memory().working).toEqual({ current: 'SECOND_VALUE' });

    component.input = 'Use memory'; component.analyze();
    const message = http.expectOne(`/api/dialogs/${id}/agent/messages`);
    expect(message.request.body.taskId).toBe(secondTask);
    message.flush({ analysis: 'done', contextMetadata: { mode: 'FULL', recentMessageCount: 4, summary: null,
      summarizedMessageCount: 0, memoryUsed: [{ scope: 'WORKING', keys: ['current'], entryCount: 1 }] } });
    const save = http.expectOne(request => request.method === 'PUT' && request.url === `/api/dialogs/${id}`);
    const archived = JSON.stringify(save.request.body.state);
    expect(archived).not.toContain('SECOND_VALUE');
    expect(archived).not.toContain('FIRST_VALUE');
    expect(save.request.body.state.exchanges[0].free.contextMetadata.memoryUsed).toEqual([
      { scope: 'WORKING', keys: ['current'], entryCount: 1 },
    ]);
    save.flush(dialog()); fixture.detectChanges();
    const evidence = fixture.nativeElement.querySelector('.memory-used');
    expect(evidence.textContent).toContain('WORKING');
    expect(evidence.textContent).toContain('current');
    expect(evidence.textContent).toContain('count: 1');
    expect(evidence.textContent).not.toContain('SECOND_VALUE');
  });

  it('shows backend availability without agent requests or overlapping heartbeats', () => {
    component.selectAgent(); fixture.detectChanges();
    expect(component.backendStatus()).toBe('CONNECTING');
    expect(fixture.nativeElement.querySelector('.server-status')?.textContent).toContain('Подключение');
    const first = http.expectOne('/api/health');
    expect(first.request.method).toBe('GET');
    component.checkBackend(); http.expectNone('/api/health');
    first.flush({ status: 'UP' }); flushTopology(); flushMemory(); fixture.detectChanges();
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
    expect(request.request.body).toEqual({ input: 'next', contextMode: 'FULL', recentMessageCount: 4, agentModelKey: 'DEEPSEEK' });
    request.flush({ analysis: 'recovered' }); flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('recovered');
  });

  it('sends Summary Recent settings and renders separate summary metrics', () => {
    component.experiment = 'AGENT'; component.contextMode = 'SUMMARY_RECENT'; component.recentMessageCount = 3;
    component.input = 'compare context'; component.analyze();
    const request = http.expectOne(`/api/dialogs/${dialog().id}/agent/messages`);
    expect(request.request.body).toEqual({ input: 'compare context', contextMode: 'SUMMARY_RECENT', recentMessageCount: 3, agentModelKey: 'DEEPSEEK' });
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
    expect(request.request.body).toEqual({ input: 'window probe', contextMode: 'SLIDING_WINDOW', recentMessageCount: 2, agentModelKey: 'DEEPSEEK' });
    request.flush({ analysis: 'window answer', metrics: { currentRequestTokens: 1, contextTokens: 5, responseTokens: 2 },
      summaryMetrics: null, factsMetrics: [], contextMetadata: { mode: 'SLIDING_WINDOW', recentMessageCount: 2, summary: null, summarizedMessageCount: 0 } });
    flushSave();

    component.contextMode = 'STICKY_FACTS'; component.recentMessageCount = 3; component.input = 'facts probe'; component.analyze();
    request = http.expectOne(`/api/dialogs/${dialog().id}/agent/messages`);
    expect(request.request.body).toEqual({ input: 'facts probe', contextMode: 'STICKY_FACTS', recentMessageCount: 3, agentModelKey: 'DEEPSEEK' });
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
    flushMemory(id);
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
    flushHealth(); flushTopology(id, topology, checkpoints); flushMemory(id);

    component.selectCheckpoint('checkpoint-2');
    http.expectOne(r => r.method === 'PUT').flush(dialog());
    component.selectAgent();
    flushHealth();
    flushTopology(id, topology, checkpoints);
    flushMemory(id);
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
    const details = fixture.nativeElement.querySelector('.technical-details') as HTMLDetailsElement;
    expect(details.open).toBe(false);
    expect(details.textContent).toContain('Sticky facts maintenance · calls: 1');
    expect(details.textContent).toContain('2 / 4 / 3');
    expect(details.textContent).not.toContain('Day 8/9');
  });

  it('renders summary maintenance metrics alongside an Agent error without main metrics', () => {
    component.experiment = 'AGENT'; component.input = 'summary failure'; component.analyze();
    http.expectOne(`/api/dialogs/${dialog().id}/agent/messages`).flush({ error: 'main overflow', factsMetrics: [],
      summaryMetrics: { currentRequestTokens: 5, contextTokens: 8, responseTokens: 2, providerUsage: null } },
      { status: 413, statusText: 'Payload Too Large' });
    flushSave(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('main overflow');
    const details = fixture.nativeElement.querySelector('.technical-details') as HTMLDetailsElement;
    expect(details.open).toBe(false);
    expect(details.textContent).toContain('Summary generation');
    expect(details.textContent).toContain('5 / 8 / 2');
    expect(details.textContent).not.toContain('Day 8/9');
  });

  it('blocks branch send during topology loading and shows the next completed response with metrics', () => {
    const id = dialog().id;
    component.experiment = 'AGENT'; component.branchId = 'branch-a'; component.input = 'branch turn';
    component.selectAgent(); fixture.detectChanges();
    flushHealth();
    flushMemory(id);
    const button = [...fixture.nativeElement.querySelectorAll('button')]
      .find((item: HTMLButtonElement) => item.textContent?.trim() === 'Отправить') as HTMLButtonElement;
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
    expect(request.request.body).toEqual({ input: 'branch turn', contextMode: 'FULL', recentMessageCount: 4, agentModelKey: 'DEEPSEEK', branchId: 'branch-a' });
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
    http.expectOne(`/api/dialogs/${a}/agent/memory`).flush({ taskId: null, shortTerm: { stale: 'old' }, working: {}, longTerm: {} });
    flushTopology(b);
    flushMemory(b);
    expect(component.memory().shortTerm).toEqual({});
    expect(component.branchId).toBeNull();

    component.selectAgent();
    flushHealth();
    http.expectOne(`/api/dialogs/${b}/agent/branches`).flush({ error: 'unavailable' }, { status: 500, statusText: 'Server Error' });
    flushMemory(b);
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

  it('loads managed Tasks, creates one and changes selection only through the narrow endpoint', () => {
    fixture.destroy();
    const task = managedTask();
    fixture = TestBed.createComponent(App); component = fixture.componentInstance as unknown as TestApp; fixture.detectChanges();
    http.expectOne('/api/agent-model-options').flush([{ key: 'DEEPSEEK', provider: 'DEEPSEEK', label: 'deepseek-v4-flash' }]);
    http.expectOne('/api/profiles').flush([]); http.expectOne('/api/tasks').flush([task]);
    http.expectOne('/api/dialogs').flush([]); http.expectOne('/api/dialogs').flush(dialog());
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(task.goal);
    expect(fixture.nativeElement.querySelector('.task-create-form')).toBeNull();
    component.toggleTaskCreation();
    expect(component.taskCreationOpen).toBe(true);
    component.taskGoalDraft = 'Новая задача'; component.createTask();
    const create = http.expectOne('/api/tasks'); expect(create.request.body).toEqual({ goal: 'Новая задача' });
    const created = managedTask('44444444-4444-4444-4444-444444444444'); create.flush(created);
    const select = http.expectOne(`/api/dialogs/${dialog().id}/task-selection`); expect(select.request.body).toEqual({ taskId: created.id }); select.flush(dialog());
    flushMemory(dialog().id, created.id);
    component.chooseTask(null);
    const clear = http.expectOne(`/api/dialogs/${dialog().id}/task-selection`); expect(clear.request.body).toEqual({ taskId: null }); clear.flush(dialog());
    flushMemory();
  });

  it('shows a legacy scope without fabricating a Task and adopts it explicitly', () => {
    const legacy = '33333333-3333-3333-3333-333333333333';
    component.openDialog('22222222-2222-2222-2222-222222222222');
    http.expectOne('/api/dialogs/22222222-2222-2222-2222-222222222222').flush(dialog('22222222-2222-2222-2222-222222222222', [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, appliedTaskId: legacy,
    }));
    http.expectOne(`/api/tasks/${legacy}`).flush({ error: 'missing' }, { status: 404, statusText: 'Not Found' });
    flushHealth(); flushTopology('22222222-2222-2222-2222-222222222222'); flushMemory('22222222-2222-2222-2222-222222222222', legacy); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Устаревшая область рабочей памяти');
    component.taskGoalDraft = 'Принятая legacy задача'; component.adoptLegacyScope();
    const adopt = http.expectOne('/api/tasks/adopt'); expect(adopt.request.body).toEqual({ id: legacy, goal: 'Принятая legacy задача' }); adopt.flush(managedTask(legacy));
    expect(component.currentTask()?.id).toBe(legacy);
  });

  it('keeps an adopted Task when an older legacy lookup later returns 404', () => {
    const dialogId = '22222222-2222-2222-2222-222222222222';
    const legacy = '33333333-3333-3333-3333-333333333333';
    component.openDialog(dialogId);
    http.expectOne(`/api/dialogs/${dialogId}`).flush(dialog(dialogId, [], {
      experiment: 'AGENT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, appliedTaskId: legacy,
    }));
    const delayedLookup = http.expectOne(`/api/tasks/${legacy}`);
    flushHealth(); flushTopology(dialogId);
    flushMemory(dialogId, legacy);

    component.taskGoalDraft = 'Принятая legacy задача'; component.adoptLegacyScope();
    const adoption = http.expectOne('/api/tasks/adopt');
    const adopted = managedTask(legacy); adoption.flush(adopted);
    expect(component.tasks().find(task => task.id === legacy)).toEqual(adopted);
    expect(component.currentTask()?.id).toBe(legacy);
    expect(component.appliedTaskId).toBe(legacy);

    delayedLookup.flush({ error: 'missing' }, { status: 404, statusText: 'Not Found' });
    expect(component.tasks().find(task => task.id === legacy)).toEqual(adopted);
    expect(component.currentTask()?.id).toBe(legacy);
    expect(component.appliedTaskId).toBe(legacy);
  });

  it('sends observed revision for lifecycle actions and reloads rather than retrying a stale action', () => {
    const task = managedTask(); (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([task]);
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory(); selectTask(task); flushMemory(dialog().id, task.id);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Планирование');
    expect(fixture.nativeElement.textContent).toContain('Активна');
    expect(fixture.nativeElement.textContent).toContain('Подготовить и утвердить план');
    expect(fixture.nativeElement.textContent).toContain('Утвердить план');
    expect(fixture.nativeElement.textContent).toContain('Ревизия');
    component.taskPlanDraft = 'Короткий план'; component.applyTaskAction('APPROVE_PLAN');
    const approve = http.expectOne(`/api/tasks/${task.id}/actions`); expect(approve.request.body).toEqual({ action: 'APPROVE_PLAN', expectedRevision: 0, approvedPlan: 'Короткий план' });
    const execution = { ...task, state: { ...task.state, stage: 'EXECUTION', currentStep: 'Выполнить', expectedAction: 'UPDATE_CURRENT_STEP', revision: 1 }, approvedPlan: 'Короткий план' }; approve.flush(execution);
    component.taskStepDraft = 'Реализовать'; component.applyTaskAction('UPDATE_CURRENT_STEP');
    const update = http.expectOne(`/api/tasks/${task.id}/actions`); expect(update.request.body.expectedRevision).toBe(1);
    update.flush({ code: 'STALE_REVISION', error: 'stale' }, { status: 409, statusText: 'Conflict' });
    http.expectOne(`/api/tasks/${task.id}`).flush(execution);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Состояние задачи изменилось');
  });

  it('exposes pause/resume, validation acceptance and disables agent send for PAUSED or DONE Task', () => {
    const task = managedTask(); (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([task]);
    component.selectAgent(); flushHealth(); flushTopology(); flushMemory(); selectTask(task); flushMemory(dialog().id, task.id);
    component.applyTaskAction('PAUSE');
    const pause = http.expectOne(`/api/tasks/${task.id}/actions`); expect(pause.request.body).toEqual({ action: 'PAUSE', expectedRevision: 0 });
    const paused = { ...task, state: { ...task.state, status: 'PAUSED', revision: 1 }, allowedActions: ['RESUME'] }; pause.flush(paused); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('На паузе');
    expect(fixture.nativeElement.textContent).toContain('Продолжить');
    component.input = 'Не отправлять'; component.analyze(); http.expectNone(`/api/dialogs/${dialog().id}/agent/messages`);
    component.applyTaskAction('RESUME'); const resume = http.expectOne(`/api/tasks/${task.id}/actions`); expect(resume.request.body.expectedRevision).toBe(1);
    const execution = { ...task, state: { ...task.state, stage: 'EXECUTION', revision: 2 }, allowedActions: ['START_VALIDATION', 'UPDATE_CURRENT_STEP', 'PAUSE'] }; resume.flush(execution);
    component.executionResultDraft = 'Реализовано'; component.applyTaskAction('START_VALIDATION'); const start = http.expectOne(`/api/tasks/${task.id}/actions`); expect(start.request.body).toEqual({ action: 'START_VALIDATION', expectedRevision: 2, executionResult: 'Реализовано' });
    const validation = { ...execution, state: { ...execution.state, stage: 'VALIDATION', revision: 3 }, executionResult: 'Реализовано', allowedActions: ['ACCEPT_VALIDATION', 'VALIDATION_FAILED', 'UPDATE_CURRENT_STEP', 'PAUSE'] }; start.flush(validation);
    component.validationEvidenceDraft = 'Проверки прошли'; component.applyTaskAction('ACCEPT_VALIDATION');
    const accept = http.expectOne(`/api/tasks/${task.id}/actions`); expect(accept.request.body).toEqual({ action: 'ACCEPT_VALIDATION', expectedRevision: 3, validationEvidence: 'Проверки прошли' });
    accept.flush({ ...validation, state: { ...validation.state, stage: 'DONE', status: 'COMPLETED', revision: 4 }, validationEvidence: 'Проверки прошли', allowedActions: [] }); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Завершено');
    expect(fixture.nativeElement.textContent).toContain('Завершена');
    expect(fixture.nativeElement.textContent).toContain('Доказательства проверки');
    expect(fixture.nativeElement.textContent).not.toContain('Пауза');
    expect(fixture.nativeElement.querySelector('[aria-label="Текущий шаг задачи"]')).toBeNull();
  });

  it('selects a created Task only for its origin Dialog after switching to another Dialog', () => {
    const a = dialog().id;
    const b = '22222222-2222-2222-2222-222222222222';
    component.taskGoalDraft = 'Создана в A'; component.taskCreationOpen = true; component.createTask();
    const create = http.expectOne('/api/tasks');
    component.openDialog(b); http.expectOne(`/api/dialogs/${b}`).flush(dialog(b));
    component.taskCreationOpen = true; component.taskGoalDraft = 'Черновик B';

    const created = managedTask('44444444-4444-4444-4444-444444444444'); create.flush(created);
    const selection = http.expectOne(`/api/dialogs/${a}/task-selection`);
    expect(selection.request.body).toEqual({ taskId: created.id }); selection.flush(dialog(a));
    expect(component.appliedTaskId).toBeNull();
    expect(component.taskCreationOpen).toBe(true);
    expect(component.taskGoalDraft).toBe('Черновик B');
    expect(component.tasks().some(task => task.id === created.id)).toBe(true);

    component.openDialog(a);
    http.expectOne(`/api/dialogs/${a}`).flush(dialog(a, [], { experiment: 'FORMAT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, appliedTaskId: created.id }));
    http.expectOne(`/api/tasks/${created.id}`).flush(created);
    expect(component.currentTask()?.id).toBe(created.id);
  });

  it('keeps the current Dialog untouched when legacy adoption started in another Dialog resolves', () => {
    const a = dialog().id;
    const b = '22222222-2222-2222-2222-222222222222';
    const legacy = '33333333-3333-3333-3333-333333333333';
    component.openDialog(b); http.expectOne(`/api/dialogs/${b}`).flush(dialog(b));
    component.openDialog(a);
    http.expectOne(`/api/dialogs/${a}`).flush(dialog(a, [], { experiment: 'FORMAT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, appliedTaskId: legacy }));
    http.expectOne(`/api/tasks/${legacy}`).flush({ error: 'missing' }, { status: 404, statusText: 'Not Found' });
    component.taskGoalDraft = 'Принять legacy'; component.adoptLegacyScope();
    const adoption = http.expectOne('/api/tasks/adopt');
    component.openDialog(b); http.expectOne(`/api/dialogs/${b}`).flush(dialog(b));
    component.taskGoalDraft = 'Черновик B';

    adoption.flush(managedTask(legacy));
    expect(component.appliedTaskId).toBeNull();
    expect(component.currentTask()).toBeNull();
    expect(component.taskGoalDraft).toBe('Черновик B');
    expect(component.tasks().some(task => task.id === legacy)).toBe(true);
  });

  it('keeps Task Y in the Inspector when a Task X action resolves after switching Dialogs', () => {
    const a = dialog().id;
    const b = '22222222-2222-2222-2222-222222222222';
    const x = managedTask('33333333-3333-3333-3333-333333333333');
    const y = managedTask('44444444-4444-4444-4444-444444444444');
    (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([x, y]);
    selectTask(x); flushMemory(a, x.id);
    component.applyTaskAction('PAUSE');
    const action = http.expectOne(`/api/tasks/${x.id}/actions`);
    component.openDialog(b);
    http.expectOne(`/api/dialogs/${b}`).flush(dialog(b, [], { experiment: 'FORMAT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, appliedTaskId: y.id }));
    http.expectOne(`/api/tasks/${y.id}`).flush(y);

    action.flush({ ...x, state: { ...x.state, status: 'PAUSED', revision: 1 } });
    expect(component.appliedTaskId).toBe(y.id);
    expect(component.currentTask()?.id).toBe(y.id);
  });

  it('does not replace a newer Task revision with a delayed GET response', () => {
    const a = dialog().id;
    const b = '22222222-2222-2222-2222-222222222222';
    const x = managedTask();
    const newer = managedTask(x.id, 6);
    component.openDialog(b); http.expectOne(`/api/dialogs/${b}`).flush(dialog(b));
    component.openDialog(a);
    http.expectOne(`/api/dialogs/${a}`).flush(dialog(a, [], { experiment: 'FORMAT', selectedMode: 'FREE', selectedStrategy: 'DIRECT', selectedTemperature: 0, appliedTaskId: x.id }));
    const delayed = http.expectOne(`/api/tasks/${x.id}`);
    (component as unknown as { acceptTask(task: unknown, dialogId: string): void }).acceptTask(newer, a);

    delayed.flush(managedTask(x.id, 5));
    expect(component.currentTask()?.state.revision).toBe(6);
    expect(component.tasks().find(task => task.id === x.id)?.state.revision).toBe(6);
  });

  it('accepts same or newer Task revisions for the selected Inspector', () => {
    const a = dialog().id;
    const x = managedTask();
    const same = { ...x, goal: 'Обновлённая задача' };
    const newer = managedTask(x.id, 1);
    (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([x]);
    selectTask(x); flushMemory(a, x.id);

    (component as unknown as { acceptTask(task: unknown, dialogId: string): void }).acceptTask(same, a);
    expect(component.currentTask()?.id).toBe(x.id);
    (component as unknown as { acceptTask(task: unknown, dialogId: string): void }).acceptTask(newer, a);
    expect(component.currentTask()?.state.revision).toBe(1);
  });

  it('creates and shows effective USER invariants in the Inspector', () => {
    http.expectOne('/api/invariants/effective').flush([]);
    component.newInvariant();
    component.invariantScope = 'USER'; component.invariantNameDraft = 'Локальный стек'; component.invariantRuleDraft = 'Использовать Java 21.';
    component.saveInvariant();
    const create = http.expectOne('/api/invariants');
    expect(create.request.body).toEqual({ scope: 'USER', taskId: null, name: 'Локальный стек', rule: 'Использовать Java 21.' });
    const invariant = { id: 'cccccccc-cccc-cccc-cccc-cccccccccccc', scope: 'USER', taskId: null, name: 'Локальный стек', rule: 'Использовать Java 21.' };
    create.flush(invariant);
    http.expectOne('/api/invariants/effective').flush([invariant]);
    expect(component.effectiveInvariants()).toEqual([invariant]);
  });

  it('loads USER invariants automatically for a dialog without a managed Task', () => {
    const initial = http.expectOne('/api/invariants/effective');
    const invariant = { id: 'cccccccc-cccc-cccc-cccc-cccccccccccc', scope: 'USER', taskId: null, name: 'Java', rule: 'Use Java 21.' };
    initial.flush([invariant]);
    expect(component.effectiveInvariants()).toEqual([invariant]);
  });

  it('refreshes effective rules on Task selection and ignores a stale previous scope result', () => {
    const userLoad = http.expectOne('/api/invariants/effective');
    const task = managedTask();
    (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([task]);
    component.chooseTask(task.id);
    http.expectOne(`/api/dialogs/${dialog().id}/task-selection`).flush(dialog());
    flushMemory(dialog().id, task.id);
    const taskLoad = http.expectOne(`/api/invariants/effective?taskId=${task.id}`);
    const taskInvariant = { id: 'dddddddd-dddd-dddd-dddd-dddddddddddd', scope: 'TASK', taskId: task.id, name: 'PostgreSQL', rule: 'Keep PostgreSQL.' };
    taskLoad.flush([taskInvariant]);
    userLoad.flush([{ id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', scope: 'USER', taskId: null, name: 'stale', rule: 'stale' }]);
    expect(component.effectiveInvariants()).toEqual([taskInvariant]);
  });

  it('ignores a stale effective-invariant loading error from the previous scope', () => {
    const userLoad = http.expectOne('/api/invariants/effective');
    const task = managedTask();
    (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([task]);
    component.chooseTask(task.id);
    http.expectOne(`/api/dialogs/${dialog().id}/task-selection`).flush(dialog());
    flushMemory(dialog().id, task.id);
    http.expectOne(`/api/invariants/effective?taskId=${task.id}`).flush([]);
    userLoad.flush({ error: 'old scope unavailable' }, { status: 500, statusText: 'Server Error' });
    expect(component.invariantsError()).toBe('');
  });

  it('loads USER invariants again when a managed Task is cleared', () => {
    http.expectOne('/api/invariants/effective').flush([]);
    const task = managedTask();
    (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([task]);
    component.chooseTask(task.id);
    http.expectOne(`/api/dialogs/${dialog().id}/task-selection`).flush(dialog());
    flushMemory(dialog().id, task.id); http.expectOne(`/api/invariants/effective?taskId=${task.id}`).flush([]);
    component.chooseTask(null);
    http.expectOne(`/api/dialogs/${dialog().id}/task-selection`).flush(dialog());
    flushMemory(dialog().id, null); http.expectOne('/api/invariants/effective').flush([]);
  });

  it('closes a TASK invariant editor on Task switch and cannot reassign its rule', () => {
    http.expectOne('/api/invariants/effective').flush([]);
    const taskA = managedTask('aaaaaaaa-1111-1111-1111-111111111111');
    const taskB = managedTask('bbbbbbbb-2222-2222-2222-222222222222');
    (component as unknown as { tasks: { set(value: unknown): void } }).tasks.set([taskA, taskB]);
    component.chooseTask(taskA.id);
    http.expectOne(`/api/dialogs/${dialog().id}/task-selection`).flush(dialog()); flushMemory(dialog().id, taskA.id);
    http.expectOne(`/api/invariants/effective?taskId=${taskA.id}`).flush([]);
    const invariant = { id: 'dddddddd-dddd-dddd-dddd-dddddddddddd', scope: 'TASK' as const, taskId: taskA.id, name: 'PostgreSQL', rule: 'Keep PostgreSQL.' };
    component.editInvariant(invariant);
    component.chooseTask(taskB.id);
    expect(component.invariantEditing).toBe(false);
    component.saveInvariant();
    http.expectNone(`/api/invariants/${invariant.id}`);
    http.expectOne(`/api/dialogs/${dialog().id}/task-selection`).flush(dialog()); flushMemory(dialog().id, taskB.id);
    http.expectOne(`/api/invariants/effective?taskId=${taskB.id}`).flush([]);
  });
});
