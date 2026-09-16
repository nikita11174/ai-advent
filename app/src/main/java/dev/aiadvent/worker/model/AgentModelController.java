package dev.aiadvent.worker.model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
class AgentModelController {
    private final AgentModelCatalog models;

    AgentModelController(AgentModelCatalog models) { this.models = models; }

    @GetMapping("/api/agent-model-options")
    List<AgentModelCatalog.Option> options() { return models.options(); }
}
