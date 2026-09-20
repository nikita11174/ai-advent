package dev.aiadvent.worker.api;

import dev.aiadvent.worker.dialog.DialogStore;
import dev.aiadvent.worker.agent.AgentDialogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dialogs")
class DialogController {
    private final DialogStore store;
    private final AgentDialogService agents;

    DialogController(DialogStore store, AgentDialogService agents) {
        this.store = store;
        this.agents = agents;
    }

    @PostMapping
    DialogStore.DialogDocument create() throws IOException {
        return store.create();
    }

    @GetMapping
    List<DialogStore.DialogSummary> list() throws IOException {
        return store.list();
    }

    @GetMapping("/{id}")
    DialogStore.DialogDocument load(@PathVariable String id) throws IOException {
        return store.load(id);
    }

    @PutMapping("/{id}")
    DialogStore.DialogDocument update(@PathVariable String id, @RequestBody DialogStore.DialogUpdate update)
            throws IOException {
        return store.update(id, update);
    }

    @PutMapping("/{id}/profile-selection")
    DialogStore.DialogDocument updateProfileSelection(@PathVariable String id,
                                                       @RequestBody ProfileSelection selection) throws IOException {
        return store.updateProfileSelection(id, selection.profileId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable java.util.UUID id) throws IOException {
        agents.deleteDialog(id);
    }

    @ExceptionHandler(DialogStore.DialogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError notFound(DialogStore.DialogNotFoundException exception) {
        return new ApiError(exception.getMessage());
    }

    record ApiError(String error) {
    }

    record ProfileSelection(UUID profileId) {
    }

    @PutMapping("/{id}/task-selection")
    DialogStore.DialogDocument updateTaskSelection(@PathVariable String id,
                                                    @RequestBody TaskSelection selection) throws IOException {
        return store.updateTaskSelection(id, selection.taskId());
    }

    record TaskSelection(UUID taskId) {
    }
}
