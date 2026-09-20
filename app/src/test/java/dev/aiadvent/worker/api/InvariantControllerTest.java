package dev.aiadvent.worker.api;

import dev.aiadvent.worker.invariant.Invariant;
import dev.aiadvent.worker.invariant.InvariantScope;
import dev.aiadvent.worker.invariant.InvariantService;
import dev.aiadvent.worker.task.TaskStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvariantController.class)
class InvariantControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private InvariantService invariants;

    @Test
    void createsUpdatesAndReturnsEffectiveRules() throws Exception {
        UUID taskId = UUID.randomUUID();
        Invariant rule = new Invariant(UUID.randomUUID(), InvariantScope.TASK, taskId, "Persistence", "Keep PostgreSQL.");
        when(invariants.create(InvariantScope.TASK, taskId, "Persistence", "Keep PostgreSQL.")).thenReturn(rule);
        when(invariants.update(rule.id(), InvariantScope.TASK, taskId, "Persistence", "Keep PostgreSQL.")).thenReturn(rule);
        when(invariants.effective(taskId)).thenReturn(List.of(rule));

        String body = "{\"scope\":\"TASK\",\"taskId\":\"" + taskId + "\",\"name\":\"Persistence\",\"rule\":\"Keep PostgreSQL.\"}";
        mvc.perform(post("/api/invariants").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(rule.id().toString()));
        mvc.perform(put("/api/invariants/{id}", rule.id()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("TASK"));
        mvc.perform(get("/api/invariants/effective").param("taskId", taskId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Persistence"));
    }

    @Test
    void mapsUnknownTaskToNotFound() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(invariants.create(InvariantScope.TASK, taskId, "Persistence", "Keep PostgreSQL."))
                .thenThrow(new TaskStore.TaskNotFoundException(taskId));
        mvc.perform(post("/api/invariants").contentType("application/json")
                        .content("{\"scope\":\"TASK\",\"taskId\":\"" + taskId + "\",\"name\":\"Persistence\",\"rule\":\"Keep PostgreSQL.\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").isNotEmpty());
    }
}
