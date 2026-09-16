import { Component, input, output } from '@angular/core';

@Component({
  standalone: true,
  selector: 'app-agent-inspector',
  templateUrl: './agent-inspector.html',
  styleUrl: './agent-inspector.scss',
})
export class AgentInspector {
  readonly open = input(false);
  readonly closed = output<void>();
}
