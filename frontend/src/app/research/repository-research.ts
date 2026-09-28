import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RepositoryResearchService, ResearchFailure, ResearchResult, SavedReport } from './repository-research.service';

@Component({
  selector: 'app-repository-research',
  imports: [DatePipe, FormsModule],
  templateUrl: './repository-research.html',
  styleUrl: './repository-research.scss',
})
export class RepositoryResearch {
  private readonly api = inject(RepositoryResearchService);
  query = '';
  maxResults = 5;
  readonly running = signal(false);
  readonly result = signal<ResearchResult | null>(null);
  readonly failure = signal<ResearchFailure | null>(null);
  readonly report = signal<SavedReport | null>(null);
  readonly reportLoading = signal(false);
  readonly reportError = signal('');
  readonly requestedMaxResults = signal(5);

  validInput(): boolean {
    const query = this.query;
    return !!query.trim() && query.length <= 100 && !/[\x00-\x1f\x7f]/.test(query)
      && Number.isInteger(this.maxResults) && this.maxResults >= 1 && this.maxResults <= 20;
  }

  run(): void {
    const query = this.query;
    if (this.running() || !this.validInput()) return;
    this.requestedMaxResults.set(this.maxResults);
    this.running.set(true); this.result.set(null); this.failure.set(null); this.report.set(null); this.reportError.set('');
    this.api.run(query, this.maxResults).subscribe({
      next: result => { this.result.set(result); this.running.set(false); },
      error: (response: HttpErrorResponse) => {
        const body = response.error as Partial<ResearchFailure> | null;
        this.failure.set(body?.status === 'FAILED' || body?.status === 'UNKNOWN'
          ? { status: body.status, failedStep: body.failedStep ?? 'UNKNOWN',
              stepsCompleted: body.stepsCompleted ?? 0, code: body.code ?? 'UNKNOWN' }
          : { status: response.status === 0 || response.status >= 500 ? 'UNKNOWN' : 'FAILED',
              failedStep: 'UNKNOWN', stepsCompleted: 0, code: 'HTTP_ERROR' });
        this.running.set(false);
      },
    });
  }

  openReport(): void {
    const ref = this.result()?.receipt.reportRef;
    if (!ref || this.reportLoading() || this.report()?.reportRef === ref) return;
    this.reportLoading.set(true); this.reportError.set('');
    this.api.report(ref).subscribe({
      next: report => { this.report.set(report); this.reportLoading.set(false); },
      error: () => { this.reportError.set('Сохранённый отчёт не удалось открыть.'); this.reportLoading.set(false); },
    });
  }
}
