import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { marked, Renderer } from 'marked';

interface ReviewResponse {
  analysis: string;
}

interface Exchange {
  id: number;
  input: string;
  analysis?: string;
  error?: string;
  loading: boolean;
}

const markdownRenderer = new Renderer();
markdownRenderer.html = ({ text }) => text
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;');

@Component({
  imports: [FormsModule, MatButtonModule, MatInputModule, MatProgressBarModule, MatToolbarModule],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  private readonly http = inject(HttpClient);
  private nextExchangeId = 1;
  private programmaticScroll = false;
  private shouldFollowLatest = true;

  @ViewChild('conversation') private conversation?: ElementRef<HTMLElement>;
  @ViewChild('composerInput') private composerInput?: ElementRef<HTMLTextAreaElement>;

  protected input = '';
  protected readonly exchanges = signal<readonly Exchange[]>([]);
  protected readonly loading = signal(false);
  protected readonly showLatestButton = signal(false);

  protected analyze(): void {
    const input = this.input;
    if (!input.trim() || this.loading()) {
      return;
    }

    const id = this.nextExchangeId++;
    this.shouldFollowLatest = this.isNearBottom();
    this.exchanges.update((exchanges) => [...exchanges, { id, input, loading: true }]);
    this.input = '';
    this.loading.set(true);
    this.requestScrollToLatest();
    this.resetComposerHeight();

    this.http.post<ReviewResponse>('/api/review', { input }).subscribe({
      next: (response) => {
        this.updateExchange(id, { analysis: response.analysis, loading: false });
        this.loading.set(false);
        this.requestScrollToLatest();
      },
      error: (error: HttpErrorResponse) => {
        const message = error.status === 0
          ? 'Не удалось подключиться к серверу. Проверьте, что backend запущен.'
          : 'Не удалось получить ответ ментора. Попробуйте ещё раз.';
        this.updateExchange(id, { error: message, loading: false });
        this.loading.set(false);
        this.requestScrollToLatest();
      },
    });
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
    if (this.programmaticScroll) {
      return;
    }
    this.shouldFollowLatest = this.isNearBottom();
    this.showLatestButton.set(!this.shouldFollowLatest);
  }

  protected scrollToLatest(): void {
    const element = this.conversation?.nativeElement;
    if (!element) {
      return;
    }
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

  private updateExchange(id: number, update: Partial<Exchange>): void {
    this.exchanges.update((exchanges) => exchanges.map((exchange) =>
      exchange.id === id ? { ...exchange, ...update } : exchange));
  }

  private requestScrollToLatest(): void {
    requestAnimationFrame(() => {
      if (this.shouldFollowLatest) {
        this.scrollToLatest();
      }
    });
  }

  private isNearBottom(): boolean {
    const element = this.conversation?.nativeElement;
    return !element || element.scrollHeight - element.scrollTop - element.clientHeight < 120;
  }

  private resetComposerHeight(): void {
    const textarea = this.composerInput?.nativeElement;
    if (textarea) {
      textarea.style.height = 'auto';
    }
  }
}
