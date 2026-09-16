import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgentInspector } from './agent-inspector';

describe('AgentInspector', () => {
  let fixture: ComponentFixture<AgentInspector>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AgentInspector] }).compileComponents();
    fixture = TestBed.createComponent(AgentInspector);
  });

  it('renders the inspector when opened', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.inspector.open')).not.toBeNull();
  });
});
