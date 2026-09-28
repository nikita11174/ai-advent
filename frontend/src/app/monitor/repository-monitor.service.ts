import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface MonitorView {
  monitorId: string;
  repositoryRef: string;
  enabled: boolean;
  intervalSeconds: number | null;
  configRevision: number;
  nextRunAt: string | null;
  aggregate: {
    successCount: number; failureCount: number; dirtySampleCount: number;
    headTransitionCount: number; branchTransitionCount: number;
    firstSuccessAt: string | null; lastSuccessAt: string | null; lastCompletedAt: string | null;
    lastOutcome: 'SUCCESS' | 'FAILURE' | null; lastFailureCode: string | null;
    latestStatus: {
      repositoryRef: string; observedAt: string; headState: string; branch: string | null;
      head: string | null; dirty: boolean;
      changeCounts: { staged: number; unstaged: number; untracked: number; conflicted: number; submoduleChanged: number } | null;
    } | null;
  };
  latestDigest: { snapshotRevision: number; generatedAt: string; text: string } | null;
  lastCommand: { commandId: string; action: 'START' | 'STOP'; intervalSeconds: number | null;
    operationStatus: string; configRevision: number } | null;
  health: { status: 'IDLE' | 'WAITING' | 'RUNNING' | 'FAULTED'; code: string | null };
}

export interface MonitorCommand {
  commandId: string;
  expectedRevision: number;
  intervalSeconds?: number;
}

export interface MonitorCommandResponse {
  operationStatus: 'APPLIED' | 'ALREADY_APPLIED' | 'UNKNOWN';
  receipt: { commandId: string; configRevision: number; enabled: boolean } | null;
  view: MonitorView | null;
}

@Injectable({ providedIn: 'root' })
export class RepositoryMonitorService {
  private readonly http = inject(HttpClient);
  configuration() { return this.http.get<{ minimumIntervalSeconds: number; maximumIntervalSeconds: number;
    defaultIntervalSeconds: number }>('/api/repository-monitor/configuration'); }
  current() { return this.http.get<MonitorView>('/api/repository-monitor'); }
  start(command: MonitorCommand) { return this.http.post<MonitorCommandResponse>('/api/repository-monitor/start', command); }
  stop(command: MonitorCommand) { return this.http.post<MonitorCommandResponse>('/api/repository-monitor/stop', command); }
}
