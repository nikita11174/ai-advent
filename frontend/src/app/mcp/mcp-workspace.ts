import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { McpConnection, McpDiscoveryResult, McpDiscoveryService, McpTool } from './mcp-discovery.service';

@Component({
  selector: 'app-mcp-workspace',
  imports: [DatePipe],
  templateUrl: './mcp-workspace.html',
  styleUrl: './mcp-workspace.scss',
})
export class McpWorkspace implements OnInit {
  private readonly api = inject(McpDiscoveryService);
  protected readonly connection = signal<McpConnection | null>(null);
  protected readonly connectionError = signal('');
  protected readonly result = signal<McpDiscoveryResult | null>(null);
  protected readonly state = signal<'idle' | 'loading' | 'success' | 'error'>('idle');
  protected readonly error = signal('');
  protected readonly query = signal('');
  protected readonly selectedTool = signal<McpTool | null>(null);
  protected readonly filteredTools = computed(() => {
    const text = this.query().trim().toLocaleLowerCase();
    return this.result()?.tools.filter(tool =>
      tool.name.toLocaleLowerCase().includes(text) || tool.description.toLocaleLowerCase().includes(text)) ?? [];
  });

  ngOnInit(): void {
    this.api.connections().subscribe({
      next: connections => this.connection.set(connections.find(item => item.id === 'idea') ?? null),
      error: () => this.connectionError.set('Не удалось загрузить настройки подключения.'),
    });
  }

  protected discover(): void {
    if (this.state() === 'loading') return;
    this.state.set('loading');
    this.result.set(null);
    this.selectedTool.set(null);
    this.query.set('');
    this.error.set('');
    this.api.discoverIdea().subscribe({
      next: result => { this.result.set(result); this.state.set('success'); },
      error: (response: HttpErrorResponse) => {
        this.error.set(this.errorText(response.error?.code));
        this.state.set('error');
      },
    });
  }

  protected select(tool: McpTool): void { this.selectedTool.set(tool); }
  protected parameters(tool: McpTool): { name: string; type: string; description: string; required: boolean }[] {
    return Object.entries(tool.inputSchema?.properties ?? {}).map(([name, value]) => ({
      name, type: value?.type ?? '—', description: value?.description ?? '',
      required: tool.inputSchema?.required?.includes(name) ?? false,
    }));
  }

  private errorText(code: string | undefined): string {
    switch (code) {
      case 'NOT_CONFIGURED': return 'Подключение IDEA MCP не настроено на сервере.';
      case 'INVALID_CONFIGURATION': return 'Настройки IDEA MCP отклонены сервером.';
      case 'TIMEOUT': return 'Время проверки истекло. Проверьте доступность IDEA MCP и повторите.';
      case 'INITIALIZE_FAILED': return 'Не удалось согласовать подключение с IDEA MCP.';
      case 'LIST_FAILED': return 'Не удалось получить список инструментов IDEA MCP.';
      case 'TOOL_LIMIT':
      case 'DESCRIPTOR_LIMIT':
      case 'OUTPUT_LIMIT':
      case 'PAGINATION_LIMIT':
      case 'MALFORMED_LIST':
      case 'MALFORMED_INITIALIZATION': return 'Ответ IDEA MCP отклонён ограничениями проверки.';
      default: return 'Проверка IDEA MCP не удалась. Убедитесь, что сервер доступен, и повторите.';
    }
  }
}
