import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { McpWorkspace } from './mcp-workspace';

describe('McpWorkspace', () => {
  let fixture: ComponentFixture<McpWorkspace>;
  let http: HttpTestingController;
  const catalog = {
    server: 'IntelliJ IDEA MCP', serverVersion: '2026.2.2', protocolVersion: '2025-11-25',
    checkedAt: '2026-09-28T10:00:00Z', toolCount: 2,
    tools: [
      { name: 'build_project', description: 'Build the project <img src=x onerror=alert(1)>', inputSchema: { type: 'object', properties: { projectPath: { type: 'string', description: 'Project path' } }, required: ['projectPath'] } },
      { name: 'get_file_problems', description: 'Inspect problems', inputSchema: { type: 'object' } },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [McpWorkspace], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(McpWorkspace);
    fixture.detectChanges();
    http.expectOne('/api/mcp/connections').flush([{ id: 'idea', name: 'IntelliJ IDEA MCP', configured: true }]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('discovers only on click, blocks repeat clicks, filters and shows plain-text details', () => {
    http.expectNone('/api/mcp/connections/idea/discovery');
    const button = fixture.nativeElement.querySelector('.connection-action button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    button.click();
    const request = http.expectOne('/api/mcp/connections/idea/discovery');
    expect(request.request.method).toBe('POST');
    request.flush(catalog);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Инструментов: 2');
    const search = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    search.value = 'build';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.tool-row').length).toBe(1);
    (fixture.nativeElement.querySelector('.tool-row') as HTMLButtonElement).click();
    fixture.detectChanges();
    const details = fixture.nativeElement.querySelector('.mcp-details') as HTMLElement;
    expect(details.textContent).toContain('Project path');
    expect(details.textContent).toContain('обязательный');
    expect(details.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(fixture.nativeElement.querySelector('.tool-row img, .mcp-details img')).toBeNull();
  });

  it('clears a previous catalog on retry failure and reload does not discover', () => {
    (fixture.nativeElement.querySelector('.connection-action button') as HTMLButtonElement).click();
    http.expectOne('/api/mcp/connections/idea/discovery').flush(catalog);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.connection-action button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.tool-row').length).toBe(0);
    http.expectOne('/api/mcp/connections/idea/discovery').flush({ code: 'INITIALIZE_FAILED' }, { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Не удалось согласовать подключение');
    expect(fixture.nativeElement.textContent).not.toContain('Инструментов: 2');

    fixture.destroy();
    fixture = TestBed.createComponent(McpWorkspace);
    fixture.detectChanges();
    http.expectOne('/api/mcp/connections').flush([{ id: 'idea', name: 'IntelliJ IDEA MCP', configured: true }]);
    http.expectNone('/api/mcp/connections/idea/discovery');
  });
});
