import { NgTemplateOutlet } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { marked, Renderer } from 'marked';

type ReviewMode = 'FREE' | 'CONTROLLED';
type Experiment = 'FORMAT' | 'REASONING';
type ReasoningStrategy = 'DIRECT' | 'STEP_BY_STEP' | 'SELF_PROMPT' | 'EXPERTS';

interface ReviewControls {
  maxTokens: number; maxFindings: number; summaryMaxWords: number;
  reasonMaxWords: number; recommendationMaxWords: number; terminationInstruction: string;
}
interface Finding { severity: 'HIGH' | 'MEDIUM' | 'LOW'; title: string; reason: string; }
interface ControlledReview { summary: string; findings: Finding[]; recommendation: string; }
interface FreeResponse { analysis: string; }
interface ControlledResponse { review: ControlledReview; rawResponse: string; }
interface ReasoningResponse { strategy: ReasoningStrategy; analysis: string; generatedPrompt?: string; }
interface ResultState {
  loading: boolean; analysis?: string; review?: ControlledReview; rawResponse?: string;
  generatedPrompt?: string; error?: string; showRaw?: boolean; showPrompt?: boolean;
  evaluation?: Evaluation;
}
interface Evaluation { found: string; missed: string; questionable: string; }
interface Exchange {
  id: number; input: string; mode: ReviewMode | 'COMPARE' | 'REASONING' | 'REASONING_COMPARE';
  controls?: ReviewControls; free?: ResultState; controlled?: ResultState;
  strategy?: ReasoningStrategy; reasoning?: Partial<Record<ReasoningStrategy, ResultState>>;
  winner?: ReasoningStrategy; winnerReason?: string;
}
interface DialogSummary { id: string; title: string; createdAt: string; updatedAt: string; }
interface DialogDocument extends DialogSummary { state: { exchanges?: Exchange[] }; }

const STRATEGIES: readonly ReasoningStrategy[] = ['DIRECT', 'STEP_BY_STEP', 'SELF_PROMPT', 'EXPERTS'];
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
const markdownRenderer = new Renderer();
markdownRenderer.html = ({ text }) => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');

@Component({
  imports: [FormsModule, NgTemplateOutlet, MatButtonModule, MatInputModule, MatProgressBarModule, MatToolbarModule],
  selector: 'app-root', styleUrl: './app.scss', templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly http = inject(HttpClient);
  private nextExchangeId = 1;
  private pendingRequests = 0;
  private programmaticScroll = false;
  private shouldFollowLatest = true;

  @ViewChild('conversation') private conversation?: ElementRef<HTMLElement>;
  @ViewChild('composerInput') private composerInput?: ElementRef<HTMLTextAreaElement>;

  protected input = '';
  protected experiment: Experiment = 'FORMAT';
  protected selectedMode: ReviewMode = 'FREE';
  protected selectedStrategy: ReasoningStrategy = 'DIRECT';
  protected controls = defaultControls();
  protected readonly strategies = STRATEGIES;
  protected readonly referenceFindings = REFERENCE_FINDINGS;
  protected readonly exchanges = signal<readonly Exchange[]>([]);
  protected readonly dialogs = signal<readonly DialogSummary[]>([]);
  protected readonly currentDialogId = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly showLatestButton = signal(false);

  ngOnInit(): void { this.loadDialogList(); }

  protected newDialog(): void {
    if (this.loading()) return;
    this.http.post<DialogDocument>('/api/dialogs', {}).subscribe({ next: dialog => this.activateDialog(dialog) });
  }

  protected openDialog(id: string): void {
    if (this.loading() || id === this.currentDialogId()) return;
    this.http.get<DialogDocument>(`/api/dialogs/${id}`).subscribe({ next: dialog => this.activateDialog(dialog) });
  }

  protected useBenchmark(): void { this.input = BENCHMARK; }

  protected analyze(): void {
    const input = this.input;
    if (!input.trim() || this.loading() || !this.currentDialogId()) return;
    if (this.experiment === 'REASONING') { this.analyzeReasoning(input); return; }
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
  private activateDialog(dialog: DialogDocument): void {
    const exchanges = dialog.state?.exchanges ?? [];
    this.currentDialogId.set(dialog.id); this.exchanges.set(exchanges);
    this.nextExchangeId = Math.max(0, ...exchanges.map(exchange => exchange.id)) + 1;
    this.loading.set(false); this.pendingRequests = 0;
    this.dialogs.update(items => [dialog, ...items.filter(item => item.id !== dialog.id)]);
    this.requestScrollToLatest();
  }
  private analyzeReasoning(input: string): void {
    const strategy = this.selectedStrategy;
    const id = this.appendExchange({ id: this.nextExchangeId++, input, mode: 'REASONING', strategy,
      reasoning: { [strategy]: { loading: true } } });
    this.prepareAfterSubmit(); this.startRequest(); this.requestReasoning(id, input, strategy);
  }
  private requestReasoning(id: number, input: string, strategy: ReasoningStrategy): void {
    this.http.post<ReasoningResponse>('/api/reasoning-review', { input, strategy }).subscribe({
      next: response => this.finishReasoning(id, strategy, { loading: false, analysis: response.analysis,
        generatedPrompt: response.generatedPrompt, evaluation: blankEvaluation() }),
      error: (error: HttpErrorResponse) => this.finishReasoning(id, strategy, { loading: false, error: this.errorMessage(error, false) }),
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
  private finishResult(id: number, side: 'free' | 'controlled', result: ResultState): void {
    this.updateExchange(id, { [side]: result }); this.completeRequest();
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
    this.shouldFollowLatest = this.isNearBottom(); this.exchanges.update(items => [...items, exchange]); return exchange.id;
  }
  private updateReasoning(id: number, strategy: ReasoningStrategy, result: ResultState, persist = true): void {
    const exchange = this.exchange(id);
    this.updateExchange(id, { reasoning: { ...exchange.reasoning, [strategy]: result } }, persist);
  }
  private updateExchange(id: number, update: Partial<Exchange>, persist = true): void {
    this.exchanges.update(items => items.map(exchange => exchange.id === id ? { ...exchange, ...update } : exchange));
    if (persist && !this.loading()) this.persistDialog();
  }
  private persistDialog(): void {
    const id = this.currentDialogId(); if (!id) return;
    const completed = this.exchanges().map(exchange => this.withoutLoading(exchange));
    const title = this.dialogTitle(completed);
    this.http.put<DialogDocument>(`/api/dialogs/${id}`, { title, state: { exchanges: completed } }).subscribe({
      next: dialog => this.dialogs.update(items => [dialog, ...items.filter(item => item.id !== dialog.id)]),
    });
  }
  private withoutLoading(exchange: Exchange): Exchange {
    const clear = (result?: ResultState) => result ? { ...result, loading: false } : result;
    return { ...exchange, free: clear(exchange.free), controlled: clear(exchange.controlled),
      reasoning: exchange.reasoning ? Object.fromEntries(Object.entries(exchange.reasoning).map(([key, value]) => [key, clear(value)])) : undefined };
  }
  private dialogTitle(exchanges: readonly Exchange[]): string {
    const source = exchanges[0]?.input.trim().replace(/\s+/g, ' ') || 'Новый диалог';
    return source.length > 64 ? source.slice(0, 61) + '…' : source;
  }
  private prepareAfterSubmit(): void { this.input = ''; this.requestScrollToLatest(); this.resetComposerHeight(); }
  private controlsSnapshot(): ReviewControls { return { ...this.controls }; }
  private exchange(id: number): Exchange { return this.exchanges().find(exchange => exchange.id === id)!; }
  private requestScrollToLatest(): void { requestAnimationFrame(() => { if (this.shouldFollowLatest) this.scrollToLatest(); }); }
  private isNearBottom(): boolean { const element = this.conversation?.nativeElement; return !element || element.scrollHeight - element.scrollTop - element.clientHeight < 120; }
  private resetComposerHeight(): void { const textarea = this.composerInput?.nativeElement; if (textarea) textarea.style.height = 'auto'; }
}
