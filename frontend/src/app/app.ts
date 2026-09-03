import { NgTemplateOutlet } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { marked, Renderer } from 'marked';

type ReviewMode = 'FREE' | 'CONTROLLED';

interface ReviewControls {
  maxTokens: number;
  maxFindings: number;
  summaryMaxWords: number;
  reasonMaxWords: number;
  recommendationMaxWords: number;
  terminationInstruction: string;
}

interface Finding { severity: 'HIGH' | 'MEDIUM' | 'LOW'; title: string; reason: string; }
interface ControlledReview { summary: string; findings: Finding[]; recommendation: string; }
interface FreeResponse { analysis: string; }
interface ControlledResponse { review: ControlledReview; rawResponse: string; }
interface ResultState {
  loading: boolean;
  analysis?: string;
  review?: ControlledReview;
  rawResponse?: string;
  error?: string;
  showRaw?: boolean;
}
interface Exchange {
  id: number;
  input: string;
  mode: ReviewMode | 'COMPARE';
  controls?: ReviewControls;
  free?: ResultState;
  controlled?: ResultState;
}

const DEFAULT_TERMINATION = 'Return exactly one JSON object. Stop immediately after the final closing brace. Do not add Markdown, explanations or text outside the JSON object.';
const defaultControls = (): ReviewControls => ({
  maxTokens: 600,
  maxFindings: 3,
  summaryMaxWords: 30,
  reasonMaxWords: 30,
  recommendationMaxWords: 30,
  terminationInstruction: DEFAULT_TERMINATION,
});

const markdownRenderer = new Renderer();
markdownRenderer.html = ({ text }) => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');

@Component({
  imports: [FormsModule, NgTemplateOutlet, MatButtonModule, MatInputModule, MatProgressBarModule, MatToolbarModule],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly http = inject(HttpClient);
  private nextExchangeId = 1;
  private pendingRequests = 0;
  private programmaticScroll = false;
  private shouldFollowLatest = true;

  @ViewChild('conversation') private conversation?: ElementRef<HTMLElement>;
  @ViewChild('composerInput') private composerInput?: ElementRef<HTMLTextAreaElement>;

  protected input = '';
  protected selectedMode: ReviewMode = 'FREE';
  protected controls = defaultControls();
  protected readonly exchanges = signal<readonly Exchange[]>([]);
  protected readonly loading = signal(false);
  protected readonly showLatestButton = signal(false);

  protected analyze(): void {
    const input = this.input;
    if (!input.trim() || this.loading()) return;

    const controls = this.selectedMode === 'CONTROLLED' ? this.controlsSnapshot() : undefined;
    const id = this.appendExchange({
      id: this.nextExchangeId++, input, mode: this.selectedMode, controls,
      [this.selectedMode === 'FREE' ? 'free' : 'controlled']: { loading: true },
    });
    this.prepareAfterSubmit();
    if (this.selectedMode === 'FREE') this.requestFree(id, input);
    else this.requestControlled(id, input, controls!);
  }

  protected compare(): void {
    const input = this.input;
    if (!input.trim() || this.loading()) return;

    const controls = this.controlsSnapshot();
    const id = this.appendExchange({
      id: this.nextExchangeId++, input, mode: 'COMPARE', controls,
      free: { loading: true }, controlled: { loading: true },
    });
    this.prepareAfterSubmit();
    this.requestFree(id, input);
    this.requestControlled(id, input, controls);
  }

  protected resetControls(): void { this.controls = defaultControls(); }

  protected toggleRaw(id: number): void {
    const exchange = this.exchange(id);
    if (exchange.controlled) {
      this.updateExchange(id, { controlled: { ...exchange.controlled, showRaw: !exchange.controlled.showRaw } });
    }
  }

  protected controlsMetadata(controls: ReviewControls): string {
    return `JSON · max ${controls.maxTokens} tokens · до ${controls.maxFindings} замечаний`;
  }

  protected handleKeyboard(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      this.analyze();
    }
  }

  protected resizeComposer(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement;
    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 220)}px`;
  }

  protected handleConversationScroll(): void {
    if (this.programmaticScroll) return;
    this.shouldFollowLatest = this.isNearBottom();
    this.showLatestButton.set(!this.shouldFollowLatest);
  }

  protected scrollToLatest(): void {
    const element = this.conversation?.nativeElement;
    if (!element) return;
    this.programmaticScroll = true;
    element.scrollTop = element.scrollHeight;
    this.shouldFollowLatest = true;
    this.showLatestButton.set(false);
    requestAnimationFrame(() => {
      this.programmaticScroll = false;
      this.shouldFollowLatest = this.isNearBottom();
      this.showLatestButton.set(!this.shouldFollowLatest);
    });
  }

  protected renderMarkdown(markdown: string): string {
    return marked.parse(markdown, { async: false, gfm: true, renderer: markdownRenderer });
  }

  private requestFree(id: number, input: string): void {
    this.startRequest();
    this.http.post<FreeResponse>('/api/review', { input, mode: 'FREE' }).subscribe({
      next: (response) => this.finishResult(id, 'free', { analysis: response.analysis, loading: false }),
      error: (error: HttpErrorResponse) => this.finishResult(id, 'free', { error: this.errorMessage(error, false), loading: false }),
    });
  }

  private requestControlled(id: number, input: string, controls: ReviewControls): void {
    this.startRequest();
    this.http.post<ControlledResponse>('/api/review', { input, mode: 'CONTROLLED', controls }).subscribe({
      next: (response) => this.finishResult(id, 'controlled', { review: response.review, rawResponse: response.rawResponse, loading: false }),
      error: (error: HttpErrorResponse) => this.finishResult(id, 'controlled', {
        error: this.errorMessage(error, true), rawResponse: error.error?.rawResponse, loading: false,
      }),
    });
  }

  private errorMessage(error: HttpErrorResponse, controlled: boolean): string {
    if (error.status === 0) return 'Не удалось подключиться к серверу. Проверьте, что backend запущен.';
    if (controlled) return error.error?.error
      ? `Контролируемый ответ не прошёл проверку: ${error.error.error}`
      : 'Контролируемый ответ не прошёл проверку.';
    return 'Не удалось получить ответ ментора. Попробуйте ещё раз.';
  }

  private startRequest(): void { this.pendingRequests++; this.loading.set(true); }

  private finishResult(id: number, side: 'free' | 'controlled', result: ResultState): void {
    this.updateExchange(id, { [side]: result });
    this.pendingRequests--;
    this.loading.set(this.pendingRequests > 0);
    this.requestScrollToLatest();
  }

  private appendExchange(exchange: Exchange): number {
    this.shouldFollowLatest = this.isNearBottom();
    this.exchanges.update((exchanges) => [...exchanges, exchange]);
    return exchange.id;
  }

  private prepareAfterSubmit(): void {
    this.input = '';
    this.requestScrollToLatest();
    this.resetComposerHeight();
  }

  private controlsSnapshot(): ReviewControls { return { ...this.controls }; }
  private exchange(id: number): Exchange { return this.exchanges().find((exchange) => exchange.id === id)!; }

  private updateExchange(id: number, update: Partial<Exchange>): void {
    this.exchanges.update((exchanges) => exchanges.map((exchange) => exchange.id === id ? { ...exchange, ...update } : exchange));
  }

  private requestScrollToLatest(): void {
    requestAnimationFrame(() => { if (this.shouldFollowLatest) this.scrollToLatest(); });
  }

  private isNearBottom(): boolean {
    const element = this.conversation?.nativeElement;
    return !element || element.scrollHeight - element.scrollTop - element.clientHeight < 120;
  }

  private resetComposerHeight(): void {
    const textarea = this.composerInput?.nativeElement;
    if (textarea) textarea.style.height = 'auto';
  }
}
