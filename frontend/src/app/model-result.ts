import { Component, input } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { marked, Renderer } from 'marked';

export interface ModelProfile { key: string; label: string; provider: string; modelId: string; modelUrl: string; }
export interface ModelResponse {
  model: ModelProfile; returnedModel: string | null; status: string | null; incompleteReason: string | null;
  serviceTier: string | null; startedAt: string; apiLatencyMs: number | null; httpStatus: number | null;
  analysis: string | null; error: string | null;
  usage: { inputTokens: number | null; outputTokens: number | null; totalTokens: number | null;
    cachedInputTokens: number | null; cacheWriteInputTokens: number | null; reasoningTokens: number | null };
  cost: { status: string; amount: string | null; reason: string | null; snapshot: { id: string; currency: string; [key: string]: unknown } };
  configuration: { version: string; [key: string]: unknown };
}
export interface ModelResultState { loading: boolean; response?: ModelResponse; error?: string; }
const renderer = new Renderer();
renderer.html = ({ text }) => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');

@Component({
  selector: 'app-model-result', imports: [MatProgressBarModule],
  styles: `:host { display:block; min-width:0; overflow-wrap:anywhere; } dl { display:grid;grid-template-columns:1fr 1fr;gap:5px;font-size:12px;color:#59667e; } dd { margin:0; } .error { color:#8f1d2c; } a { color:#2563eb; }`,
  template: `
    <h3><a [href]="profile().modelUrl" target="_blank" rel="noopener noreferrer">{{ profile().label }}</a></h3>
    @if (result()?.loading) { <p>Ментор анализирует...</p><mat-progress-bar mode="indeterminate" /> }
    @if (result()?.error) { <p class="error" role="alert">{{ result()?.error }}</p> }
    @if (result()?.response; as response) {
      <dl>
        <dt>Запрошена</dt><dd>{{ response.model.modelId }}</dd>
        <dt>Возвращена</dt><dd>{{ response.returnedModel ?? 'UNKNOWN' }}</dd>
        <dt>API время</dt><dd>{{ response.apiLatencyMs === null ? 'UNKNOWN' : response.apiLatencyMs + ' мс' }}</dd>
        <dt>Tokens input / output / total</dt><dd>{{ response.usage.inputTokens ?? 'UNKNOWN' }} / {{ response.usage.outputTokens ?? 'UNKNOWN' }} / {{ response.usage.totalTokens ?? 'UNKNOWN' }}</dd>
        <dt>Cache read / write</dt><dd>{{ response.usage.cachedInputTokens ?? 'UNKNOWN' }} / {{ response.usage.cacheWriteInputTokens ?? 'UNKNOWN' }}</dd>
        <dt>Reasoning tokens</dt><dd>{{ response.usage.reasoningTokens ?? 'UNKNOWN' }}</dd>
        <dt>Статус / причина</dt><dd>{{ response.status ?? 'UNKNOWN' }} / {{ response.incompleteReason ?? '—' }}</dd>
        <dt>Service tier</dt><dd>{{ response.serviceTier ?? 'UNKNOWN' }}</dd>
        <dt>Стоимость (оценка)</dt><dd>{{ response.cost.amount === null ? 'UNKNOWN' : response.cost.amount + ' ' + response.cost.snapshot.currency }}</dd>
      </dl>
      @if (response.cost.reason) { <p>{{ response.cost.reason }}</p> }
      <details><summary>Тарифный snapshot</summary><small>{{ response.cost.snapshot.id }} · {{ response.configuration.version }}</small></details>
      @if (response.analysis) { <div class="analysis-text" [innerHTML]="markdown(response.analysis)"></div> }
    }
  `,
})
export class ModelResult {
  readonly profile = input.required<ModelProfile>();
  readonly result = input<ModelResultState>();
  protected markdown(value: string): string { return marked.parse(value, { async: false, gfm: true, renderer }); }
}
