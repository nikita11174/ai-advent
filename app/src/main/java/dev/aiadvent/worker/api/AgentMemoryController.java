package dev.aiadvent.worker.api;

import dev.aiadvent.worker.dialog.DialogStore;
import dev.aiadvent.worker.memory.AgentMemory;
import dev.aiadvent.worker.agent.AgentDialogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/dialogs/{id}/agent/memory")
class AgentMemoryController {
    private final AgentDialogService agents;

    AgentMemoryController(AgentDialogService agents) {
        this.agents = agents;
    }

    @GetMapping
    AgentMemory.Snapshot memory(@PathVariable UUID id, @RequestParam(required = false) UUID taskId)
            throws IOException {
        return agents.memory(id, taskId);
    }

    @PutMapping("/{scope}")
    AgentMemory.Snapshot upsert(@PathVariable UUID id, @PathVariable AgentMemory.Scope scope,
                                @RequestBody UpsertRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Memory upsert body is required.");
        }
        return agents.upsertMemory(id, request.taskId(), scope, request.key(), request.value());
    }

    @ExceptionHandler(DialogStore.DialogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError notFound(DialogStore.DialogNotFoundException exception) {
        return new ApiError(exception.getMessage(), null);
    }

    record UpsertRequest(UUID taskId, String key, String value) {
    }
}
