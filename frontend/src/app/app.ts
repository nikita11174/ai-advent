import { JsonPipe, NgTemplateOutlet } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, computed, ElementRef, inject, OnDestroy, OnInit, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { marked, Renderer } from 'marked';
import { forkJoin, timeout } from 'rxjs';
import { ModelProfile, ModelResponse, ModelResult, ModelResultState } from './model-result';
import { AgentModelOption } from './agent/agent-ui.types';
import { AgentModelService } from './agent/model/agent-model.service';
import { AgentInspector } from './agent/inspector/agent-inspector';

type ReviewMode = 'FREE' | 'CONTROLLED';
type Experiment = 'FORMAT' | 'REASONING' | 'TEMPERATURE' | 'MODELS' | 'AGENT';
type ReasoningStrategy = 'DIRECT' | 'STEP_BY_STEP' | 'SELF_PROMPT' | 'EXPERTS';
type Temperature = 0 | 0.7 | 1.2;
type ContextMode = 'FULL' | 'SUMMARY_RECENT' | 'SLIDING_WINDOW' | 'STICKY_FACTS';
type MemoryScope = 'SHORT_TERM' | 'WORKING' | 'LONG_TERM';
interface ReviewControls {
  maxTokens: number; maxFindings: number; summaryMaxWords: number;
  reasonMaxWords: number; recommendationMaxWords: number; terminationInstruction: string;
}
interface Finding { severity: 'HIGH' | 'MEDIUM' | 'LOW'; title: string; reason: string; }
interface ControlledReview { summary: string; findings: Finding[]; recommendation: string; }
interface FreeResponse { analysis: string; }
interface ProviderUsage { promptTokens: number | null; completionTokens: number | null; totalTokens: number | null; }
interface TokenMetrics {
  currentRequestTokens: number; contextTokens: number; responseTokens: number; providerUsage: ProviderUsage | null;
}
interface StickyFacts { coveredUserMessageCount: number; facts: Record<string, string>; }
interface MemoryUsage { scope: MemoryScope; keys: string[]; entryCount: number; }
interface ContextMetadata { mode: ContextMode; recentMessageCount: number; summary: string | null; summarizedMessageCount: number; facts?: StickyFacts | null; memoryUsed?: MemoryUsage[]; }
interface MemorySnapshot {
  taskId: string | null; shortTerm: Record<string, string>; working: Record<string, string>; longTerm: Record<string, string>;
}
interface AgentResponse extends FreeResponse { metrics: TokenMetrics; summaryMetrics: TokenMetrics | null; factsMetrics: TokenMetrics[]; contextMetadata: ContextMetadata; }
interface AgentError { error?: string; summaryMetrics?: TokenMetrics | null; factsMetrics?: TokenMetrics[]; }
interface ControlledResponse { review: ControlledReview; rawResponse: string; }
interface ReasoningResponse { strategy: ReasoningStrategy; analysis: string; generatedPrompt?: string; }
interface TemperatureResponse { temperature: Temperature; analysis: string; }
interface ResultState {
  agentModelKey?: string;
  loading: boolean; analysis?: string; review?: ControlledReview; rawResponse?: string;
  generatedPrompt?: string; error?: string; showRaw?: boolean; showPrompt?: boolean;
  evaluation?: Evaluation; metrics?: TokenMetrics; summaryMetrics?: TokenMetrics | null; factsMetrics?: TokenMetrics[]; contextMetadata?: ContextMetadata;
}
interface Evaluation { found: string; missed: string; questionable: string; }
interface TemperatureEvaluation extends Evaluation { creativity: string; diversity: string; suitableTasks: string; }
interface TemperatureConclusion { accuracy: string; creativity: string; diversity: string; taskFit: string; }
interface Exchange {
  id: number; input: string; mode: ReviewMode | 'COMPARE' | 'REASONING' | 'REASONING_COMPARE' | 'TEMPERATURE' | 'TEMPERATURE_COMPARE' | 'MODELS' | 'AGENT';
  branchId?: string | null;
  models?: ModelProfile[]; modelResults?: Record<string, ModelResultState>;
  modelEvaluations?: Record<string, Evaluation>; modelConclusion?: string;
  benchmarkReference?: { id: string; findings: string[] };
  controls?: ReviewControls; free?: ResultState; controlled?: ResultState;
  strategy?: ReasoningStrategy; reasoning?: Partial<Record<ReasoningStrategy, ResultState>>;
  winner?: ReasoningStrategy; winnerReason?: string;
  temperature?: Temperature; temperatureResults?: Partial<Record<Temperature, ResultState>>;
  temperatureEvaluations?: Partial<Record<Temperature, TemperatureEvaluation>>;
  temperatureConclusion?: TemperatureConclusion | string;
}
interface DialogSummary { id: string; title: string; createdAt: string; updatedAt: string; }
interface DialogUiState {
  selectedAgentModelKey?: string | null;
  selectedProfileId?: string | null;
  experiment: Experiment; selectedMode: ReviewMode; selectedStrategy: ReasoningStrategy;
  selectedTemperature: Temperature;
  selectedModelKey?: string;
  contextMode?: ContextMode; linearContextMode?: ContextMode; recentMessageCount?: number;
  branchId?: string | null; checkpointId?: string | null;
  appliedTaskId?: string | null;
}
interface DialogDocument extends DialogSummary { state: { exchanges?: Exchange[]; ui?: DialogUiState }; }
interface AgentMessage { role: 'system' | 'user' | 'assistant'; content: string; }
interface AgentBranch { id: string; checkpointId: string; history: AgentMessage[]; }
interface AgentCheckpoint { id: string; baseHistory: AgentMessage[]; branches: AgentBranch[]; }
interface AgentProfile { id: string; name: string; instructions: string; responseStyle: string; responseFormat: string; }
interface ProfileDraft { name: string; instructions: string; responseStyle: string; responseFormat: string; }

const STRATEGIES: readonly ReasoningStrategy[] = ['DIRECT', 'STEP_BY_STEP', 'SELF_PROMPT', 'EXPERTS'];
const TEMPERATURES: readonly Temperature[] = [0, 0.7, 1.2];
const STRATEGY_LABELS: Record<ReasoningStrategy, string> = {
  DIRECT: 'Прямой', STEP_BY_STEP: 'Пошаговый', SELF_PROMPT: 'Самопромпт', EXPERTS: 'Эксперты',
};
const DEFAULT_TERMINATION = 'Return exactly one JSON object. Stop immediately after the final closing brace. Do not add Markdown, explanations or text outside the JSON object.';
const BENCHMARK = `Проанализируй Java-код и найди инженерные риски. Не предполагай скрытые гарантии, которых нет в snippet.

\`\`\`java
@Transactional
public void handle(PaymentReceived event) {
    Order order = orders.findById(event.orderId()).orElseThrow();
    order.markPaid();
    email.sendReceipt(order.customerEmail());
}
\`\`\``;
const REFERENCE_FINDINGS = [
  'Нет idempotency/deduplication при повторной доставке события.',
  'Email и DB transaction не образуют атомарную границу.',
  'Concurrent delivery создаёт race и повторный внешний side effect.',
  'Ошибка email откатывает DB change и допускает повторную обработку.',
];
const defaultControls = (): ReviewControls => ({ maxTokens: 600, maxFindings: 3, summaryMaxWords: 30,
  reasonMaxWords: 30, recommendationMaxWords: 30, terminationInstruction: DEFAULT_TERMINATION });
const blankEvaluation = (): Evaluation => ({ found: '', missed: '', questionable: '' });
const blankTemperatureEvaluation = (): TemperatureEvaluation => ({ found: '', missed: '', questionable: '', creativity: '', diversity: '', suitableTasks: '' });
const blankTemperatureConclusion = (): TemperatureConclusion => ({ accuracy: '', creativity: '', diversity: '', taskFit: '' });
const markdownRenderer = new Renderer();
markdownRenderer.html = ({ text }) => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');

@Component({
  imports: [FormsModule, JsonPipe, NgTemplateOutlet, MatButtonModule, MatInputModule, MatProgressBarModule, MatToolbarModule, ModelResult, AgentInspector],
  selector: 'app-root', styleUrl: './app.scss', templateUrl: './app.html',
})
export class App implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly agentModelService = inject(AgentModelService);
  private readonly pageTitle = inject(Title);
  private heartbeatTimer?: ReturnType<typeof setInterval>;
  private heartbeatInFlight = false;
  private nextExchangeId = 1;
  private pendingRequests = 0;
  private topologyRequestGeneration = 0;
  private memoryRequestGeneration = 0;
  private programmaticScroll = false;
  private shouldFollowLatest = true;

  @ViewChild('conversation') private conversation?: ElementRef<HTMLElement>;
  @ViewChild('composerInput') private composerInput?: ElementRef<HTMLTextAreaElement>;

  protected input = '';
  protected experiment: Experiment = 'FORMAT';
  protected selectedMode: ReviewMode = 'FREE';
  protected selectedStrategy: ReasoningStrategy = 'DIRECT';
  protected selectedTemperature: Temperature = 0;
  protected contextMode: ContextMode = 'FULL';
  protected linearContextMode: ContextMode = 'FULL';
  protected recentMessageCount = 4;
  protected branchId: string | null = null;
  protected checkpointId: string | null = null;
  protected appliedTaskId: string | null = null;
  protected taskIdDraft = '';
  protected taskEditing = false;
  protected readonly memoryEditing = signal(false);
  protected taskCopyStatus = '';
  protected inspectorOpen = false;
  protected selectedAgentModelKey: string | null = null;
  protected readonly agentModelOptions = this.agentModelService.options;
  protected readonly agentModelError = this.agentModelService.error;
  protected readonly profiles = signal<readonly AgentProfile[]>([]);
  protected readonly profilesError = signal('');
  private profilesLoaded = false;
  protected selectedProfileId: string | null = null;
  protected profileEditing = false;
  protected editingProfileId: string | null = null;
  protected profileDraft: ProfileDraft = this.blankProfileDraft();
  private nextProfileEditorId = 0;
  private profileEditor: { id: number; dialogId: string | null } | null = null;
  protected readonly memoryScopes: readonly MemoryScope[] = ['SHORT_TERM', 'WORKING', 'LONG_TERM'];
  protected readonly memoryLabels: Record<MemoryScope, string> = {
    SHORT_TERM: 'Краткосрочная', WORKING: 'Рабочая', LONG_TERM: 'Долговременная',
  };
  protected readonly memoryScopeLabels: Record<MemoryScope, string> = {
    SHORT_TERM: 'Диалог', WORKING: 'Задача', LONG_TERM: 'Установка',
  };
  protected memoryScope: MemoryScope = 'SHORT_TERM';
  protected memoryKey = '';
  protected memoryValue = '';
  protected readonly memory = signal<MemorySnapshot>({ taskId: null, shortTerm: {}, working: {}, longTerm: {} });
  protected readonly memoryLoading = signal(false);
  protected readonly memorySaving = signal(false);
  protected readonly memoryError = signal('');
  protected readonly memorySuccess = signal('');
  protected readonly branches = signal<readonly AgentBranch[]>([]);
  protected readonly checkpoints = signal<readonly AgentCheckpoint[]>([]);
  protected readonly branchViews = signal<Record<string, readonly Exchange[]>>({});
  protected readonly topologyBusy = signal(false);
  protected readonly topologyError = signal('');
  protected selectedModelKey = 'WEAK';
  protected readonly modelOptions = signal<ModelProfile[]>([]);
  protected readonly modelOptionsError = signal('');
  protected controls = defaultControls();
  protected readonly strategies = STRATEGIES;
  protected readonly temperatures = TEMPERATURES;
  protected readonly referenceFindings = REFERENCE_FINDINGS;
  protected readonly exchanges = signal<readonly Exchange[]>([]);
  protected readonly dialogs = signal<readonly DialogSummary[]>([]);
  protected readonly visibleDialogCount = signal(15);
  protected readonly visibleDialogs = computed(() => this.dialogs().slice(0, this.visibleDialogCount()));
  protected readonly currentDialogId = signal<string | null>(null);
  protected dialogActionsFor: string | null = null;
  protected readonly loading = signal(false);
  protected readonly showLatestButton = signal(false);
  protected readonly sidebarOpen = signal(false);
  protected readonly backendStatus = signal<'CONNECTING' | 'ONLINE' | 'OFFLINE'>('CONNECTING');

  ngOnInit(): void {
    this.pageTitle.setTitle('Local AI Worker');
    this.loadAgentModels();
    this.loadProfiles();
    this.loadDialogList();
    this.heartbeatTimer = setInterval(() => this.checkBackend(), 2000);
  }

  ngOnDestroy(): void { clearInterval(this.heartbeatTimer); }

  protected newDialog(): void {
    if (this.loading()) return;
    this.http.post<DialogDocument>('/api/dialogs', {}).subscribe({ next: dialog => this.activateDialog(dialog, true) });
  }

  protected loadAgentModels(): void {
    this.agentModelService.load();
  }

  protected activeAgentModelKey(): string {
    return this.selectedAgentModelKey ?? this.agentModelOptions()[0]?.key ?? '';
  }

  protected agentModelAvailable(): boolean {
    return this.agentModelOptions().some(model => model.key === this.activeAgentModelKey());
  }

  protected chooseAgentModel(key: string): void {
    if (this.loading() || !this.agentModelOptions().some(model => model.key === key)) return;
    this.selectedAgentModelKey = key;
    this.persistDialog();
  }

  protected agentModelLabel(key?: string): string {
    const model = this.agentModelOptions().find(option => option.key === key);
    return model ? `${model.provider === 'OPENAI' ? 'OpenAI API' : 'DeepSeek API'} · ${model.label}` : 'Модель не указана';
  }

  protected loadProfiles(): void {
    this.http.get<AgentProfile[]>('/api/profiles').subscribe({
      next: profiles => {
        this.profiles.set(profiles);
        this.profilesLoaded = true;
        this.profilesError.set('');
        this.normalizeSelectedProfile();
      },
      error: () => this.profilesError.set('Не удалось загрузить профили.'),
    });
  }

  protected selectedProfile(): AgentProfile | undefined {
    return this.profiles().find(profile => profile.id === this.selectedProfileId);
  }

  protected chooseProfile(id: string | null): void {
    if (this.loading() || (id && !this.profiles().some(profile => profile.id === id))) return;
    this.selectedProfileId = id;
    this.persistDialog();
  }

  protected newProfile(): void {
    this.editingProfileId = null;
    this.profileDraft = this.blankProfileDraft();
    this.profileEditing = true;
    this.profileEditor = { id: ++this.nextProfileEditorId, dialogId: this.currentDialogId() };
    this.profilesError.set('');
  }

  protected editProfile(profile: AgentProfile): void {
    this.editingProfileId = profile.id;
    this.profileDraft = { name: profile.name, instructions: profile.instructions,
      responseStyle: profile.responseStyle, responseFormat: profile.responseFormat };
    this.profileEditing = true;
    this.profileEditor = { id: ++this.nextProfileEditorId, dialogId: this.currentDialogId() };
    this.profilesError.set('');
  }

  protected cancelProfileEdit(): void {
    this.profileEditing = false;
    this.editingProfileId = null;
    this.profileDraft = this.blankProfileDraft();
    this.profileEditor = null;
  }

  protected saveProfile(): void {
    if (!this.profileDraft.name.trim()) {
      this.profilesError.set('Название профиля обязательно.');
      return;
    }
    const body = { ...this.profileDraft, name: this.profileDraft.name.trim() };
    const editingProfileId = this.editingProfileId;
    const originDialogId = this.currentDialogId();
    const editor = this.profileEditor;
    const request = editingProfileId
      ? this.http.put<AgentProfile>(`/api/profiles/${this.editingProfileId}`, body)
      : this.http.post<AgentProfile>('/api/profiles', body);
    request.subscribe({
      next: profile => {
        this.profiles.update(items => [...items.filter(item => item.id !== profile.id), profile]
          .sort((left, right) => left.name.localeCompare(right.name)));
        if (!editingProfileId && originDialogId) this.selectProfileForDialog(originDialogId, profile.id);
        if (this.isCurrentProfileEditor(editor)) this.cancelProfileEdit();
        if (editingProfileId) this.persistDialog();
      },
      error: (error: HttpErrorResponse) => this.profilesError.set(error.error?.error ?? 'Не удалось сохранить профиль.'),
    });
  }

  protected editMemory(scope: MemoryScope = 'SHORT_TERM', key = '', value = ''): void {
    this.memoryScope = scope; this.memoryKey = key; this.memoryValue = value; this.memoryEditing.set(true);
    this.memoryError.set(''); this.memorySuccess.set('');
  }

  protected closeMemoryEditor(): void {
    this.memoryEditing.set(false); this.memoryKey = ''; this.memoryValue = '';
  }

  protected async copyTaskId(): Promise<void> {
    if (!this.appliedTaskId) return;
    try { await navigator.clipboard.writeText(this.appliedTaskId); this.taskCopyStatus = 'Скопировано'; }
    catch { this.taskCopyStatus = 'Не удалось скопировать'; }
  }

  protected latestAgentResult(): ResultState | undefined {
    return [...this.visibleExchanges()].reverse().find(exchange => exchange.mode === 'AGENT' && !exchange.free?.loading)?.free;
  }

  protected selectAgent(): void {
    this.experiment = 'AGENT';
    this.backendStatus.set('CONNECTING');
    this.checkBackend();
    this.loadTopology();
    this.loadMemory();
  }

  protected generateTaskScope(): void {
    this.taskIdDraft = crypto.randomUUID();
    this.applyTaskScope();
  }

  protected applyTaskScope(): void {
    const taskId = this.taskIdDraft.trim();
    if (!this.isUuid(taskId)) {
      this.memoryError.set('Task ID должен быть UUID.');
      return;
    }
    this.appliedTaskId = taskId;
    this.taskEditing = false; this.taskCopyStatus = ''; this.closeMemoryEditor();
    this.taskIdDraft = taskId;
    this.persistDialog();
    this.loadMemory();
  }

  protected saveMemory(): void {
    const dialogId = this.currentDialogId();
    if (!dialogId || this.memorySaving() || !this.memoryKey.trim() || !this.memoryValue.trim()
      || (this.memoryScope === 'WORKING' && !this.appliedTaskId)) return;
    const taskId = this.appliedTaskId;
    const operation = ++this.memoryRequestGeneration;
    this.memorySaving.set(true); this.memoryError.set(''); this.memorySuccess.set('');
    this.http.put<MemorySnapshot>(`/api/dialogs/${dialogId}/agent/memory/${this.memoryScope}`,
      { taskId, key: this.memoryKey, value: this.memoryValue }).subscribe({
      next: () => {
        if (!this.isCurrentMemoryOperation(dialogId, taskId, operation)) return;
        this.memorySaving.set(false); this.memoryKey = ''; this.memoryValue = '';
        this.memoryEditing.set(false);
        this.memorySuccess.set('Память сохранена.'); this.loadMemory();
      },
      error: (error: HttpErrorResponse) => {
        if (!this.isCurrentMemoryOperation(dialogId, taskId, operation)) return;
        this.memorySaving.set(false);
        this.memoryError.set(error.error?.error ?? 'Не удалось сохранить memory.');
      },
    });
  }

  protected memoryEntries(scope: MemoryScope): readonly [string, string][] {
    const values = scope === 'SHORT_TERM' ? this.memory().shortTerm
      : scope === 'WORKING' ? this.memory().working : this.memory().longTerm;
    return Object.entries(values);
  }

  private checkBackend(): void {
    if (this.experiment !== 'AGENT' || this.heartbeatInFlight) return;
    this.heartbeatInFlight = true;
    this.http.get('/api/health').pipe(timeout(1500)).subscribe({
      next: () => { this.backendStatus.set('ONLINE'); this.heartbeatInFlight = false; },
      error: () => { this.backendStatus.set('OFFLINE'); this.heartbeatInFlight = false; },
    });
  }

  protected createCheckpoint(): void {
    const id = this.startTopologyOperation(); if (!id) return;
    const operation = this.topologyRequestGeneration;
    this.http.post<{ id: string }>(`/api/dialogs/${id}/agent/checkpoints`, this.branchId ? { branchId: this.branchId } : {}).subscribe({
      next: checkpoint => {
        if (!this.isCurrentTopologyOperation(id, operation)) return;
        this.checkpoints.update(items => [...items, { ...checkpoint, baseHistory: [], branches: [] }]);
        this.checkpointId = checkpoint.id;
        this.finishTopologyOperation(id, operation); this.persistDialog();
      },
      error: () => this.failTopologyOperation(id, operation, 'Не удалось создать checkpoint.'),
    });
  }

  protected createBranch(): void {
    if (!this.checkpointId) return;
    const id = this.startTopologyOperation(); if (!id) return;
    const operation = this.topologyRequestGeneration;
    const checkpointId = this.checkpointId;
    this.http.post<AgentBranch>(`/api/dialogs/${id}/agent/checkpoints/${checkpointId}/branches`, {}).subscribe({
      next: branch => {
        if (!this.isCurrentTopologyOperation(id, operation)) return;
        this.branches.update(items => [...items, branch]);
        this.branchViews.update(views => ({ ...views, [branch.id]: this.exchangesFromHistory(branch) }));
        this.branchId = branch.id; this.contextMode = 'FULL';
        this.finishTopologyOperation(id, operation); this.persistDialog();
      },
      error: () => this.failTopologyOperation(id, operation, 'Не удалось создать ветку.'),
    });
  }

  protected switchBranch(branchId: string | null): void {
    if (this.topologyBusy()) return;
    this.branchId = branchId;
    if (branchId) {
      this.contextMode = 'FULL';
      if (!this.branchViews()[branchId]) this.loadTopology();
    } else {
      this.contextMode = this.linearContextMode;
    }
    this.persistDialog();
  }

  protected selectCheckpoint(checkpointId: string | null): void { this.checkpointId = checkpointId; this.persistDialog(); }
  protected selectLinearContextMode(mode: ContextMode): void {
    this.linearContextMode = mode; this.contextMode = mode;
    this.persistDialog();
  }

  protected openDialog(id: string): void {
    if (this.loading() || id === this.currentDialogId()) return;
    this.http.get<DialogDocument>(`/api/dialogs/${id}`).subscribe({ next: dialog => this.activateDialog(dialog) });
  }

  protected showMoreDialogs(): void { this.visibleDialogCount.update(count => count + 15); }

  protected toggleDialogActions(id: string, event: Event): void {
    event.stopPropagation();
    this.dialogActionsFor = this.dialogActionsFor === id ? null : id;
  }

  protected deleteDialog(id: string, title: string, event: Event): void {
    event.stopPropagation();
    if (this.loading() || !window.confirm(`Удалить диалог «${title}»?`)) return;
    this.http.delete(`/api/dialogs/${id}`).subscribe({ next: () => {
      const remaining = this.dialogs().filter(dialog => dialog.id !== id);
      this.dialogActionsFor = null;
      this.dialogs.set(remaining);
      if (id !== this.currentDialogId()) return;
      if (remaining.length) this.openDialog(remaining[0].id); else this.newDialog();
    }});
  }

  protected toggleSidebar(): void { this.sidebarOpen.update(open => !open); }
  protected visibleExchanges(): readonly Exchange[] {
    return this.branchId ? this.branchViews()[this.branchId] ?? [] : this.exchanges().filter(exchange => exchange.branchId == null);
  }

  protected selectModels(): void {
    this.experiment = 'MODELS';
    if (this.modelOptions().length) return;
    this.http.get<ModelProfile[]>('/api/model-options').subscribe({
      next: profiles => { this.modelOptions.set(profiles); this.modelOptionsError.set(''); },
      error: () => this.modelOptionsError.set('Не удалось загрузить модели. Проверьте backend и откройте вкладку снова.'),
    });
  }

  protected compareModels(): void { this.runModels(this.modelOptions()); }

  private runModels(models: ModelProfile[]): void {
    const input = this.input;
    if (!input.trim() || !models.length || this.loading() || !this.currentDialogId()) return;
    const snapshot = structuredClone(models);
    const id = this.appendExchange({ id: this.nextExchangeId++, input, mode: 'MODELS', models: snapshot,
      modelResults: Object.fromEntries(snapshot.map(model => [model.key, { loading: true }])),
      modelEvaluations: Object.fromEntries(snapshot.map(model => [model.key, blankEvaluation()])),
      benchmarkReference: input === BENCHMARK ? { id: 'payment-received-v1', findings: [...REFERENCE_FINDINGS] } : undefined });
    this.prepareAfterSubmit(); this.pendingRequests += snapshot.length; this.loading.set(true);
    for (const model of snapshot) {
      this.http.post<ModelResponse>('/api/model-review', { input, modelKey: model.key }).subscribe({
        next: response => this.finishModel(id, model.key, { loading: false, response }),
        error: (error: HttpErrorResponse) => this.finishModel(id, model.key, { loading: false,
          response: error.error?.model ? error.error : undefined, error: this.errorMessage(error, false) }),
      });
    }
  }

  private finishModel(id: number, key: string, result: ModelResultState): void {
    this.updateExchange(id, { modelResults: { ...this.exchange(id).modelResults, [key]: result } });
    this.completeRequest();
  }

  protected updateModelEvaluation(id: number, key: string, field: keyof Evaluation, value: string): void {
    const exchange = this.exchange(id);
    this.updateExchange(id, { modelEvaluations: { ...exchange.modelEvaluations,
      [key]: { ...blankEvaluation(), ...exchange.modelEvaluations?.[key], [field]: value } } }, false);
  }

  protected updateModelConclusion(id: number, value: string): void {
    this.updateExchange(id, { modelConclusion: value }, false);
  }

  protected useBenchmark(): void { this.input = BENCHMARK; }

  protected analyze(): void {
    const input = this.input;
    if (!input.trim() || this.loading() || !this.currentDialogId() || (this.experiment === 'AGENT' && this.topologyBusy())) return;
    if (this.experiment === 'AGENT') { if (this.agentModelAvailable()) this.analyzeAgent(input); return; }
    if (this.experiment === 'MODELS') { this.runModels(this.modelOptions().filter(model => model.key === this.selectedModelKey)); return; }
    if (this.experiment === 'REASONING') { this.analyzeReasoning(input); return; }
    if (this.experiment === 'TEMPERATURE') { this.analyzeTemperature(input); return; }
    const controls = this.selectedMode === 'CONTROLLED' ? this.controlsSnapshot() : undefined;
    const id = this.appendExchange({ id: this.nextExchangeId++, input, mode: this.selectedMode, controls,
      [this.selectedMode === 'FREE' ? 'free' : 'controlled']: { loading: true } });
    this.prepareAfterSubmit();
    if (this.selectedMode === 'FREE') this.requestFree(id, input); else this.requestControlled(id, input, controls!);
  }

  protected compare(): void {
    const input = this.input;
    if (!input.trim() || this.loading() || !this.currentDialogId()) return;
    const controls = this.controlsSnapshot();
    const id = this.appendExchange({ id: this.nextExchangeId++, input, mode: 'COMPARE', controls,
      free: { loading: true }, controlled: { loading: true } });
    this.prepareAfterSubmit(); this.requestFree(id, input); this.requestControlled(id, input, controls);
  }

  protected compareStrategies(): void {
    const input = this.input;
    if (!input.trim() || this.loading() || !this.currentDialogId()) return;
    const reasoning = Object.fromEntries(STRATEGIES.map(strategy => [strategy, { loading: true }])) as Record<ReasoningStrategy, ResultState>;
    const id = this.appendExchange({ id: this.nextExchangeId++, input, mode: 'REASONING_COMPARE', reasoning });
    this.prepareAfterSubmit(); this.pendingRequests += STRATEGIES.length; this.loading.set(true);
    for (const strategy of STRATEGIES) this.requestReasoning(id, input, strategy);
  }

  protected compareTemperatures(): void {
    const input = this.input;
    if (!input.trim() || this.loading() || !this.currentDialogId()) return;
    const temperatureResults = Object.fromEntries(TEMPERATURES.map(value => [value, { loading: true }])) as Record<Temperature, ResultState>;
    const temperatureEvaluations = Object.fromEntries(TEMPERATURES.map(value => [value, blankTemperatureEvaluation()])) as Record<Temperature, TemperatureEvaluation>;
    const id = this.appendExchange({ id: this.nextExchangeId++, input, mode: 'TEMPERATURE_COMPARE', temperatureResults, temperatureEvaluations });
    this.prepareAfterSubmit(); this.pendingRequests += TEMPERATURES.length; this.loading.set(true);
    for (const temperature of TEMPERATURES) this.requestTemperature(id, input, temperature);
  }

  protected resetControls(): void { this.controls = defaultControls(); }
  protected strategyLabel(strategy: ReasoningStrategy): string { return STRATEGY_LABELS[strategy]; }
  protected controlsMetadata(controls: ReviewControls): string { return `JSON · max ${controls.maxTokens} tokens · до ${controls.maxFindings} замечаний`; }
  protected renderMarkdown(markdown: string): string { return marked.parse(markdown, { async: false, gfm: true, renderer: markdownRenderer }); }

  protected toggleRaw(id: number): void {
    const exchange = this.exchange(id);
    if (exchange.controlled) this.updateExchange(id, { controlled: { ...exchange.controlled, showRaw: !exchange.controlled.showRaw } }, false);
  }

  protected togglePrompt(id: number, strategy: ReasoningStrategy): void {
    const exchange = this.exchange(id); const result = exchange.reasoning?.[strategy];
    if (result) this.updateReasoning(id, strategy, { ...result, showPrompt: !result.showPrompt }, false);
  }

  protected updateEvaluation(id: number, strategy: ReasoningStrategy, field: keyof Evaluation, value: string): void {
    const exchange = this.exchange(id); const result = exchange.reasoning?.[strategy];
    if (!result) return;
    this.updateReasoning(id, strategy, { ...result, evaluation: { ...(result.evaluation ?? blankEvaluation()), [field]: value } }, false);
  }

  protected updateWinner(id: number, winner: ReasoningStrategy | undefined, reason: string): void {
    this.updateExchange(id, { winner, winnerReason: reason }, false);
  }

  protected saveEvaluation(): void { this.persistDialog(); }

  protected updateTemperatureEvaluation(id: number, temperature: Temperature, field: keyof TemperatureEvaluation, value: string): void {
    const exchange = this.exchange(id); const current = exchange.temperatureEvaluations?.[temperature] ?? blankTemperatureEvaluation();
    this.updateExchange(id, { temperatureEvaluations: { ...exchange.temperatureEvaluations, [temperature]: { ...current, [field]: value } } }, false);
  }

  protected updateTemperatureConclusion(id: number, field: keyof TemperatureConclusion, value: string): void {
    const exchange = this.exchange(id); const conclusion = this.temperatureConclusion(exchange);
    this.updateExchange(id, { temperatureConclusion: { ...conclusion, [field]: value } }, false);
  }

  protected temperatureConclusion(exchange: Exchange): TemperatureConclusion {
    if (typeof exchange.temperatureConclusion === 'string') {
      return { ...blankTemperatureConclusion(), accuracy: exchange.temperatureConclusion };
    }
    return exchange.temperatureConclusion ?? blankTemperatureConclusion();
  }

  protected handleKeyboard(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') { event.preventDefault(); this.analyze(); }
  }
  protected resizeComposer(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement; textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 220)}px`;
  }
  protected handleConversationScroll(): void {
    if (this.programmaticScroll) return; this.shouldFollowLatest = this.isNearBottom();
    this.showLatestButton.set(!this.shouldFollowLatest);
  }
  protected scrollToLatest(): void {
    const element = this.conversation?.nativeElement; if (!element) return;
    this.programmaticScroll = true; element.scrollTop = element.scrollHeight;
    this.shouldFollowLatest = true; this.showLatestButton.set(false);
    requestAnimationFrame(() => { this.programmaticScroll = false; this.shouldFollowLatest = this.isNearBottom(); this.showLatestButton.set(!this.shouldFollowLatest); });
  }

  private loadDialogList(): void {
    this.http.get<DialogSummary[]>('/api/dialogs').subscribe({ next: dialogs => {
      this.dialogs.set(dialogs); if (dialogs.length) this.openDialog(dialogs[0].id); else this.newDialog();
    }});
  }
  private activateDialog(dialog: DialogDocument, fresh = false): void {
    const exchanges = (dialog.state?.exchanges ?? []).map(exchange => typeof exchange.temperatureConclusion === 'string'
      ? { ...exchange, temperatureConclusion: { ...blankTemperatureConclusion(), accuracy: exchange.temperatureConclusion } }
      : exchange);
    const ui = dialog.state?.ui;
    this.experiment = ui?.experiment ?? (fresh ? 'AGENT' : 'FORMAT'); this.selectedMode = ui?.selectedMode ?? 'FREE';
    this.selectedAgentModelKey = ui?.selectedAgentModelKey ?? null;
    this.selectedProfileId = ui?.selectedProfileId ?? null;
    this.normalizeSelectedProfile();
    this.taskEditing = false; this.taskCopyStatus = ''; this.closeMemoryEditor(); this.input = '';
    this.selectedStrategy = ui?.selectedStrategy ?? 'DIRECT'; this.selectedTemperature = ui?.selectedTemperature ?? 0;
    this.selectedModelKey = ui?.selectedModelKey ?? 'WEAK';
    this.linearContextMode = ui?.linearContextMode ?? ui?.contextMode ?? 'FULL';
    this.branchId = ui?.branchId ?? null; this.contextMode = this.branchId ? 'FULL' : this.linearContextMode;
    this.recentMessageCount = ui?.recentMessageCount ?? 4; this.checkpointId = ui?.checkpointId ?? null;
    this.appliedTaskId = ui?.appliedTaskId ?? null; this.taskIdDraft = this.appliedTaskId ?? '';
    this.topologyRequestGeneration++; this.branches.set([]); this.checkpoints.set([]); this.branchViews.set({});
    this.memoryRequestGeneration++; this.memory.set({ taskId: this.appliedTaskId, shortTerm: {}, working: {}, longTerm: {} });
    this.memoryLoading.set(false); this.memorySaving.set(false); this.memoryError.set(''); this.memorySuccess.set('');
    this.topologyBusy.set(false); this.topologyError.set('');
    if (this.experiment === 'MODELS') this.selectModels();
    this.currentDialogId.set(dialog.id); this.exchanges.set(exchanges);
    this.nextExchangeId = Math.max(0, ...exchanges.map(exchange => exchange.id)) + 1;
    this.loading.set(false); this.pendingRequests = 0;
    this.dialogs.update(items => [dialog, ...items.filter(item => item.id !== dialog.id)]);
    this.sidebarOpen.set(false);
    this.requestScrollToLatest();
    if (this.experiment === 'AGENT') this.selectAgent();
  }
  private loadTopology(): void {
    const id = this.currentDialogId(); if (!id || this.loading() || this.topologyBusy()) return;
    const operation = ++this.topologyRequestGeneration;
    this.topologyBusy.set(true); this.topologyError.set('');
    forkJoin({
      branches: this.http.get<AgentBranch[]>(`/api/dialogs/${id}/agent/branches`),
      checkpoints: this.http.get<AgentCheckpoint[]>(`/api/dialogs/${id}/agent/checkpoints`),
    }).subscribe({
      next: topology => {
        if (!this.isCurrentTopologyOperation(id, operation)) return;
        this.branches.set(topology.branches); this.checkpoints.set(topology.checkpoints);
        this.branchViews.set(Object.fromEntries(topology.branches.map(branch => [branch.id, this.exchangesFromHistory(branch)])));
        if (this.branchId && !topology.branches.some(branch => branch.id === this.branchId)) {
          this.branchId = null; this.contextMode = this.linearContextMode;
          this.topologyError.set('Выбранная ветка больше недоступна. Показан линейный диалог.');
        }
        if (!topology.checkpoints.some(checkpoint => checkpoint.id === this.checkpointId)) {
          this.checkpointId = topology.checkpoints[0]?.id ?? null;
        }
        this.finishTopologyOperation(id, operation);
      },
      error: () => this.failTopologyOperation(id, operation, 'Не удалось загрузить topology диалога.'),
    });
  }
  private loadMemory(): void {
    const dialogId = this.currentDialogId(); if (!dialogId) return;
    const taskId = this.appliedTaskId;
    const operation = ++this.memoryRequestGeneration;
    this.memory.set({ taskId, shortTerm: {}, working: {}, longTerm: {} });
    const query = taskId ? `?taskId=${encodeURIComponent(taskId)}` : '';
    this.memoryLoading.set(true); this.memoryError.set('');
    this.http.get<MemorySnapshot>(`/api/dialogs/${dialogId}/agent/memory${query}`).subscribe({
      next: snapshot => {
        if (!this.isCurrentMemoryOperation(dialogId, taskId, operation)) return;
        this.memory.set(snapshot); this.memoryLoading.set(false);
      },
      error: () => {
        if (!this.isCurrentMemoryOperation(dialogId, taskId, operation)) return;
        this.memoryLoading.set(false); this.memoryError.set('Не удалось загрузить memory.');
      },
    });
  }
  private analyzeAgent(input: string): void {
    const dialogId = this.currentDialogId(); if (!dialogId) return;
    const requestBranchId = this.branchId;
    const agentModelKey = this.activeAgentModelKey();
    const id = this.appendExchange({ id: this.nextExchangeId++, input, mode: 'AGENT', branchId: requestBranchId,
      free: { loading: true, agentModelKey } });
    this.prepareAfterSubmit(); this.startRequest();
    const request = { input, contextMode: this.contextMode, recentMessageCount: this.recentMessageCount,
      agentModelKey,
      ...(requestBranchId ? { branchId: requestBranchId } : {}), ...(this.appliedTaskId ? { taskId: this.appliedTaskId } : {}),
      ...(this.selectedProfileId ? { profileId: this.selectedProfileId } : {}) };
    this.http.post<AgentResponse>(`/api/dialogs/${dialogId}/agent/messages`,
      request).subscribe({
      next: response => this.finishAgentResult(dialogId, id, { analysis: response.analysis, metrics: response.metrics,
        summaryMetrics: response.summaryMetrics, factsMetrics: response.factsMetrics, contextMetadata: response.contextMetadata, loading: false }),
      error: (error: HttpErrorResponse) => {
        const details = error.error as AgentError | null;
        this.finishAgentResult(dialogId, id, { error: this.errorMessage(error, false),
          summaryMetrics: details?.summaryMetrics ?? null, factsMetrics: details?.factsMetrics ?? [], loading: false });
      },
    });
  }
  private analyzeReasoning(input: string): void {
    const strategy = this.selectedStrategy;
    const id = this.appendExchange({ id: this.nextExchangeId++, input, mode: 'REASONING', strategy,
      reasoning: { [strategy]: { loading: true } } });
    this.prepareAfterSubmit(); this.startRequest(); this.requestReasoning(id, input, strategy);
  }
  private analyzeTemperature(input: string): void {
    const temperature = this.selectedTemperature;
    const id = this.appendExchange({ id: this.nextExchangeId++, input, mode: 'TEMPERATURE', temperature,
      temperatureResults: { [temperature]: { loading: true } }, temperatureEvaluations: { [temperature]: blankTemperatureEvaluation() } });
    this.prepareAfterSubmit(); this.startRequest(); this.requestTemperature(id, input, temperature);
  }
  private requestReasoning(id: number, input: string, strategy: ReasoningStrategy): void {
    this.http.post<ReasoningResponse>('/api/reasoning-review', { input, strategy }).subscribe({
      next: response => this.finishReasoning(id, strategy, { loading: false, analysis: response.analysis,
        generatedPrompt: response.generatedPrompt, evaluation: blankEvaluation() }),
      error: (error: HttpErrorResponse) => this.finishReasoning(id, strategy, { loading: false, error: this.errorMessage(error, false) }),
    });
  }
  private requestTemperature(id: number, input: string, temperature: Temperature): void {
    this.http.post<TemperatureResponse>('/api/temperature-review', { input, temperature }).subscribe({
      next: response => this.finishTemperature(id, temperature, { loading: false, analysis: response.analysis }),
      error: (error: HttpErrorResponse) => this.finishTemperature(id, temperature, { loading: false, error: this.errorMessage(error, false) }),
    });
  }
  private requestFree(id: number, input: string): void {
    this.startRequest(); this.http.post<FreeResponse>('/api/review', { input, mode: 'FREE' }).subscribe({
      next: response => this.finishResult(id, 'free', { analysis: response.analysis, loading: false }),
      error: (error: HttpErrorResponse) => this.finishResult(id, 'free', { error: this.errorMessage(error, false), loading: false }),
    });
  }
  private requestControlled(id: number, input: string, controls: ReviewControls): void {
    this.startRequest(); this.http.post<ControlledResponse>('/api/review', { input, mode: 'CONTROLLED', controls }).subscribe({
      next: response => this.finishResult(id, 'controlled', { review: response.review, rawResponse: response.rawResponse, loading: false }),
      error: (error: HttpErrorResponse) => this.finishResult(id, 'controlled', { error: this.errorMessage(error, true), rawResponse: error.error?.rawResponse, loading: false }),
    });
  }
  private finishReasoning(id: number, strategy: ReasoningStrategy, result: ResultState): void {
    this.updateReasoning(id, strategy, result); this.completeRequest();
  }
  private finishTemperature(id: number, temperature: Temperature, result: ResultState): void {
    const exchange = this.exchange(id);
    this.updateExchange(id, { temperatureResults: { ...exchange.temperatureResults, [temperature]: result } });
    this.completeRequest();
  }
  private finishResult(id: number, side: 'free' | 'controlled', result: ResultState): void {
    this.updateExchange(id, { [side]: result }); this.completeRequest();
  }
  private finishAgentResult(dialogId: string, id: number, result: ResultState): void {
    if (this.currentDialogId() !== dialogId) return;
    this.updateExchange(id, { free: { ...this.exchange(id).free, ...result } }); this.completeRequest();
  }
  private completeRequest(): void {
    this.pendingRequests--; this.loading.set(this.pendingRequests > 0);
    if (!this.loading()) this.persistDialog(); this.requestScrollToLatest();
  }
  private errorMessage(error: HttpErrorResponse, controlled: boolean): string {
    if (error.status === 0) return 'Не удалось подключиться к серверу. Проверьте, что backend запущен.';
    if (controlled) return error.error?.error ? `Контролируемый ответ не прошёл проверку: ${error.error.error}` : 'Контролируемый ответ не прошёл проверку.';
    return error.error?.error ? `Не удалось получить ответ ментора: ${error.error.error}` : 'Не удалось получить ответ ментора. Попробуйте ещё раз.';
  }
  private startRequest(): void { this.pendingRequests++; this.loading.set(true); }
  private appendExchange(exchange: Exchange): number {
    this.shouldFollowLatest = this.isNearBottom(); this.exchanges.update(items => [...items, exchange]);
    if (exchange.branchId) this.branchViews.update(views => ({ ...views, [exchange.branchId!]: [...(views[exchange.branchId!] ?? []), exchange] }));
    return exchange.id;
  }
  private updateReasoning(id: number, strategy: ReasoningStrategy, result: ResultState, persist = true): void {
    const exchange = this.exchange(id);
    this.updateExchange(id, { reasoning: { ...exchange.reasoning, [strategy]: result } }, persist);
  }
  private updateExchange(id: number, update: Partial<Exchange>, persist = true): void {
    const exchange = this.exchanges().find(item => item.id === id);
    this.exchanges.update(items => items.map(item => item.id === id ? { ...item, ...update } : item));
    if (exchange?.branchId) this.branchViews.update(views => ({ ...views, [exchange.branchId!]:
      (views[exchange.branchId!] ?? []).map(item => item.id === id ? { ...item, ...update } : item) }));
    if (persist && !this.loading()) this.persistDialog();
  }
  private persistDialog(): void {
    const id = this.currentDialogId(); if (!id) return;
    const completed = this.exchanges().map(exchange => this.withoutLoading(exchange));
    const title = this.dialogTitle(completed);
    const ui: DialogUiState = { experiment: this.experiment, selectedMode: this.selectedMode,
      selectedAgentModelKey: this.selectedAgentModelKey,
      selectedProfileId: this.selectedProfileId,
      selectedStrategy: this.selectedStrategy, selectedTemperature: this.selectedTemperature, selectedModelKey: this.selectedModelKey,
      contextMode: this.contextMode, linearContextMode: this.linearContextMode, recentMessageCount: this.recentMessageCount,
      branchId: this.branchId, checkpointId: this.checkpointId, appliedTaskId: this.appliedTaskId };
    this.http.put<DialogDocument>(`/api/dialogs/${id}`, { title, state: { exchanges: completed, ui } }).subscribe({
      next: dialog => this.dialogs.update(items => [dialog, ...items.filter(item => item.id !== dialog.id)]),
    });
  }
  private withoutLoading(exchange: Exchange): Exchange {
    const clear = (result?: ResultState) => result ? { ...result, loading: false } : result;
    return { ...exchange, free: clear(exchange.free), controlled: clear(exchange.controlled),
      modelResults: exchange.modelResults ? Object.fromEntries(Object.entries(exchange.modelResults).map(([key, value]) => [key, { ...value, loading: false }])) : undefined,
      reasoning: exchange.reasoning ? Object.fromEntries(Object.entries(exchange.reasoning).map(([key, value]) => [key, clear(value)])) : undefined,
      temperatureResults: exchange.temperatureResults ? Object.fromEntries(Object.entries(exchange.temperatureResults).map(([key, value]) => [key, clear(value)])) : undefined };
  }
  private dialogTitle(exchanges: readonly Exchange[]): string {
    const source = exchanges[0]?.input.trim().replace(/\s+/g, ' ') || 'Новый диалог';
    return source.length > 64 ? source.slice(0, 61) + '…' : source;
  }
  private prepareAfterSubmit(): void { this.input = ''; this.requestScrollToLatest(); this.resetComposerHeight(); }
  private controlsSnapshot(): ReviewControls { return { ...this.controls }; }
  private blankProfileDraft(): ProfileDraft { return { name: '', instructions: '', responseStyle: '', responseFormat: '' }; }
  private normalizeSelectedProfile(): void {
    if (this.profilesLoaded && this.selectedProfileId && !this.selectedProfile()) this.selectedProfileId = null;
  }
  private selectProfileForDialog(dialogId: string, profileId: string): void {
    if (this.currentDialogId() === dialogId) {
      this.selectedProfileId = profileId;
    }
    this.http.put<DialogDocument>(`/api/dialogs/${dialogId}/profile-selection`, { profileId }).subscribe({
      next: dialog => this.dialogs.update(items => [dialog, ...items.filter(item => item.id !== dialog.id)]),
    });
  }
  private isCurrentProfileEditor(editor: { id: number; dialogId: string | null } | null): boolean {
    return editor !== null && this.profileEditor?.id === editor.id && this.currentDialogId() === editor.dialogId;
  }
  private exchange(id: number): Exchange { return this.exchanges().find(exchange => exchange.id === id)!; }
  private startTopologyOperation(): string | null {
    const id = this.currentDialogId();
    if (!id || this.loading() || this.topologyBusy()) return null;
    ++this.topologyRequestGeneration; this.topologyBusy.set(true); this.topologyError.set('');
    return id;
  }
  private isCurrentTopologyOperation(dialogId: string, operation: number): boolean {
    return this.currentDialogId() === dialogId && this.topologyRequestGeneration === operation;
  }
  private isCurrentMemoryOperation(dialogId: string, taskId: string | null, operation: number): boolean {
    return this.currentDialogId() === dialogId && this.appliedTaskId === taskId && this.memoryRequestGeneration === operation;
  }
  private isUuid(value: string): boolean {
    return /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value);
  }
  private finishTopologyOperation(dialogId: string, operation: number): void {
    if (this.isCurrentTopologyOperation(dialogId, operation)) this.topologyBusy.set(false);
  }
  private failTopologyOperation(dialogId: string, operation: number, message: string): void {
    if (!this.isCurrentTopologyOperation(dialogId, operation)) return;
    this.topologyBusy.set(false); this.topologyError.set(message);
  }
  private exchangesFromHistory(branch: AgentBranch): readonly Exchange[] {
    const turns = branch.history.filter(message => message.role !== 'system');
    const exchanges: Exchange[] = [];
    for (let index = 0; index + 1 < turns.length; index += 2) {
      const user = turns[index]; const assistant = turns[index + 1];
      if (user.role !== 'user' || assistant.role !== 'assistant') break;
      exchanges.push({ id: -(index + 1), input: user.content, mode: 'AGENT', branchId: branch.id,
        free: { loading: false, analysis: assistant.content } });
    }
    return exchanges;
  }
  private requestScrollToLatest(): void { requestAnimationFrame(() => { if (this.shouldFollowLatest) this.scrollToLatest(); }); }
  private isNearBottom(): boolean { const element = this.conversation?.nativeElement; return !element || element.scrollHeight - element.scrollTop - element.clientHeight < 120; }
  private resetComposerHeight(): void { const textarea = this.composerInput?.nativeElement; if (textarea) textarea.style.height = 'auto'; }
}
