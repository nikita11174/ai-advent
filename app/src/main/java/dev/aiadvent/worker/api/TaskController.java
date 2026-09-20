package dev.aiadvent.worker.api;

import dev.aiadvent.worker.task.Task;
import dev.aiadvent.worker.task.TaskCommand;
import dev.aiadvent.worker.task.TaskService;
import dev.aiadvent.worker.task.TaskStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
class TaskController {
    private final TaskService tasks;

    TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    List<Task> list() throws IOException {
        return tasks.list();
    }

    @GetMapping("/{id}")
    Task load(@PathVariable UUID id) throws IOException {
        return tasks.load(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Task create(@RequestBody CreateRequest request) throws IOException {
        return tasks.create(request.goal());
    }

    @PostMapping("/adopt")
    @ResponseStatus(HttpStatus.CREATED)
    Task adopt(@RequestBody AdoptRequest request) throws IOException {
        return tasks.adopt(request.id(), request.goal());
    }

    @PostMapping("/{id}/actions")
    Task apply(@PathVariable UUID id, @RequestBody ActionRequest request) throws IOException {
        return tasks.apply(id, command(request), request.expectedRevision());
    }

    @ExceptionHandler(TaskStore.TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError notFound(TaskStore.TaskNotFoundException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler({TaskService.TaskRevisionMismatchException.class, TaskService.TaskTransitionException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError conflict(RuntimeException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError invalidRequest(IllegalArgumentException exception) {
        return new ApiError(exception.getMessage());
    }

    record CreateRequest(String goal) {
    }

    record AdoptRequest(UUID id, String goal) {
    }

    record ActionRequest(ActionType action, long expectedRevision, String approvedPlan, String currentStep,
                         String validationEvidence) {
    }

    enum ActionType {
        APPROVE_PLAN,
        UPDATE_CURRENT_STEP,
        START_VALIDATION,
        ACCEPT_VALIDATION,
        PAUSE,
        RESUME
    }

    record ApiError(String error) {
    }

    private static TaskCommand command(ActionRequest request) {
        if (request == null || request.action() == null) {
            throw new IllegalArgumentException("Task action is required.");
        }
        return switch (request.action()) {
            case APPROVE_PLAN -> new TaskCommand.ApprovePlan(request.approvedPlan());
            case UPDATE_CURRENT_STEP -> new TaskCommand.UpdateCurrentStep(request.currentStep());
            case START_VALIDATION -> new TaskCommand.StartValidation();
            case ACCEPT_VALIDATION -> new TaskCommand.AcceptValidation(request.validationEvidence());
            case PAUSE -> new TaskCommand.Pause();
            case RESUME -> new TaskCommand.Resume();
        };
    }
}
