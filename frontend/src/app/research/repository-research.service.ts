import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface ResearchResult {
  status: 'COMPLETED';
  stepsCompleted: number;
  matchesSeen: number;
  filesMatched: number;
  truncated: boolean;
  receipt: { reportRef: string; createdAt: string; bytesWritten: number; contentHash: string };
}

export interface ResearchFailure {
  status: 'FAILED' | 'UNKNOWN';
  failedStep: string;
  stepsCompleted: number;
  code: string;
}

export interface SavedReport { reportRef: string; content: string; }

@Injectable({ providedIn: 'root' })
export class RepositoryResearchService {
  private readonly http = inject(HttpClient);
  run(query: string, maxResults: number) {
    return this.http.post<ResearchResult>('/api/repository-research', { query, maxResults });
  }
  report(reportRef: string) {
    return this.http.get<SavedReport>(`/api/repository-research/reports/${encodeURIComponent(reportRef)}`);
  }
}
