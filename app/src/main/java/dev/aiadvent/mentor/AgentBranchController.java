package dev.aiadvent.mentor;

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
@RequestMapping("/api/dialogs/{id}/agent")
class AgentBranchController {
    private final AgentDialogService agents;

    AgentBranchController(AgentDialogService agents) {
        this.agents = agents;
    }

    @PostMapping("/checkpoints")
    AgentBranchStore.Checkpoint checkpoint(@PathVariable UUID id, @RequestBody(required = false) BranchRequest request)
            throws IOException {
        return agents.createCheckpoint(id, request == null ? null : request.branchId());
    }

    @PostMapping("/checkpoints/{checkpointId}/branches")
    AgentBranchStore.Branch branch(@PathVariable UUID id, @PathVariable String checkpointId) throws IOException {
        return agents.createBranch(id, checkpointId);
    }

    @GetMapping("/branches")
    List<AgentBranchStore.Branch> branches(@PathVariable UUID id) throws IOException {
        return agents.branches(id);
    }

    @ExceptionHandler(DialogStore.DialogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ReviewController.ApiError dialogNotFound(DialogStore.DialogNotFoundException exception) {
        return new ReviewController.ApiError(exception.getMessage(), null);
    }

    @ExceptionHandler(AgentBranchStore.BranchNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ReviewController.ApiError notFound(AgentBranchStore.BranchNotFoundException exception) {
        return new ReviewController.ApiError(exception.getMessage(), null);
    }

    record BranchRequest(String branchId) {
    }
}
