package dev.aiadvent.worker.api;

import dev.aiadvent.worker.invariant.Invariant;
import dev.aiadvent.worker.invariant.InvariantScope;
import dev.aiadvent.worker.invariant.InvariantService;
import dev.aiadvent.worker.invariant.InvariantStore;
import dev.aiadvent.worker.task.TaskStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invariants")
class InvariantController {
    private final InvariantService invariants;

    InvariantController(InvariantService invariants) {
        this.invariants = invariants;
    }

    @GetMapping
    List<Invariant> list() throws IOException {
        return invariants.list();
    }

    @GetMapping("/effective")
    List<Invariant> effective(@RequestParam(required = false) UUID taskId) throws IOException {
        return invariants.effective(taskId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Invariant create(@RequestBody Request request) throws IOException {
        return invariants.create(request.scope(), request.taskId(), request.name(), request.rule());
    }

    @PutMapping("/{id}")
    Invariant update(@PathVariable UUID id, @RequestBody Request request) throws IOException {
        return invariants.update(id, request.scope(), request.taskId(), request.name(), request.rule());
    }

    @ExceptionHandler({InvariantStore.InvariantNotFoundException.class, TaskStore.TaskNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError notFound(IllegalArgumentException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError invalidRequest(IllegalArgumentException exception) {
        return new ApiError(exception.getMessage());
    }

    record Request(InvariantScope scope, UUID taskId, String name, String rule) {
    }

    record ApiError(String error) {
    }
}
