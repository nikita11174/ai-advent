package dev.aiadvent.worker.api;

import dev.aiadvent.worker.model.AgentModelCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class AgentModelController {
    private final AgentModelCatalog models;

    public AgentModelController(AgentModelCatalog models) { this.models = models; }

    @GetMapping("/api/agent-model-options")
    List<AgentModelCatalog.Option> options() { return models.options(); }
}
