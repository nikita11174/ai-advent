import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AgentModelService } from './agent-model.service';

describe('AgentModelService', () => {
  let service: AgentModelService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AgentModelService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads only the backend-owned catalog', () => {
    service.load();
    const request = http.expectOne('/api/agent-model-options');
    request.flush([{ key: 'DEEPSEEK', provider: 'DEEPSEEK', label: 'deepseek-v4-flash' }]);

    expect(service.options()).toEqual([{ key: 'DEEPSEEK', provider: 'DEEPSEEK', label: 'deepseek-v4-flash' }]);
    expect(service.error()).toBe('');
  });
});
