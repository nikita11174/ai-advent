import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface McpConnection { id: string; name: string; configured: boolean; }
export interface McpTool { name: string; description: string; inputSchema: { type: string; properties?: Record<string, { type?: string; description?: string }>; required?: string[] }; }
export interface McpDiscoveryResult {
  server: string;
  serverVersion: string;
  protocolVersion: string;
  checkedAt: string;
  toolCount: number;
  tools: McpTool[];
}

@Injectable({ providedIn: 'root' })
export class McpDiscoveryService {
  private readonly http = inject(HttpClient);

  connections() { return this.http.get<McpConnection[]>('/api/mcp/connections'); }
  discoverIdea() { return this.http.post<McpDiscoveryResult>('/api/mcp/connections/idea/discovery', {}); }
}
