package dev.aiadvent.worker.api;

import dev.aiadvent.worker.task.Task;
import dev.aiadvent.worker.task.TaskCommand;
import dev.aiadvent.worker.task.TaskService;
import dev.aiadvent.worker.task.TaskStage;
import dev.aiadvent.worker.task.TaskState;
import dev.aiadvent.worker.task.TaskStatus;
import dev.aiadvent.worker.task.TaskStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TaskService tasks;

    @Test
    void createsListsLoadsAndExplicitlyAdoptsTasks() throws Exception {
        Task task = task(UUID.randomUUID(), 0);
        when(tasks.create("Prepare solution")).thenReturn(task);
        when(tasks.list()).thenReturn(List.of(task));
        when(tasks.load(task.id())).thenReturn(task);
        UUID legacyId = UUID.randomUUID();
        Task adopted = task(legacyId, 0);
        when(tasks.adopt(legacyId, "Adopt scope")).thenReturn(adopted);

        mvc.perform(post("/api/tasks").contentType("application/json").content("{\"goal\":\"Prepare solution\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(task.id().toString()));
        mvc.perform(get("/api/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(task.id().toString()));
        mvc.perform(get("/api/tasks/{id}", task.id())).andExpect(status().isOk()).andExpect(jsonPath("$.goal").value("Goal"));
        mvc.perform(post("/api/tasks/adopt").contentType("application/json")
                        .content("{\"id\":\"" + legacyId + "\",\"goal\":\"Adopt scope\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(legacyId.toString()));
    }

    @Test
    void appliesTypedActionAndMapsConflictsAndMissingTasks() throws Exception {
        UUID id = UUID.randomUUID();
        Task updated = task(id, 1);
        when(tasks.apply(eq(id), any(TaskCommand.ApprovePlan.class), eq(0L))).thenReturn(updated);

        mvc.perform(post("/api/tasks/{id}/actions", id).contentType("application/json")
                        .content("{\"action\":\"APPROVE_PLAN\",\"expectedRevision\":0,\"approvedPlan\":\"Plan\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state.revision").value(1));
        verify(tasks).apply(eq(id), any(TaskCommand.ApprovePlan.class), eq(0L));

        when(tasks.apply(eq(id), any(TaskCommand.Pause.class), eq(1L)))
                .thenThrow(new TaskService.TaskRevisionMismatchException(id, 1, 2));
        mvc.perform(post("/api/tasks/{id}/actions", id).contentType("application/json")
                        .content("{\"action\":\"PAUSE\",\"expectedRevision\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").isNotEmpty());

        when(tasks.apply(eq(id), any(TaskCommand.StartValidation.class), eq(1L)))
                .thenThrow(new TaskService.TaskTransitionException("Validation start is not available in PLANNING."));
        mvc.perform(post("/api/tasks/{id}/actions", id).contentType("application/json")
                        .content("{\"action\":\"START_VALIDATION\",\"expectedRevision\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").isNotEmpty());

        mvc.perform(post("/api/tasks/{id}/actions", id).contentType("application/json")
                        .content("{\"action\":\"APPROVE_PLAN\",\"expectedRevision\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Approved plan is required."));

        when(tasks.load(id)).thenThrow(new TaskStore.TaskNotFoundException(id));
        mvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").isNotEmpty());
    }

    private static Task task(UUID id, long revision) {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        return new Task(id, "Goal", new TaskState(TaskStage.PLANNING, "Plan", "Approve plan", TaskStatus.ACTIVE, revision),
                "", "", now, now);
    }
}
