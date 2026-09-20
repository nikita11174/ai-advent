package dev.aiadvent.worker.api;

import dev.aiadvent.worker.agent.AgentDialogService;
import dev.aiadvent.worker.dialog.DialogStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DialogController.class)
class DialogControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DialogStore store;
    @MockitoBean
    private AgentDialogService agents;

    @Test
    void deletesDialog() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/dialogs/{id}", id)).andExpect(status().isNoContent());

        verify(agents).deleteDialog(id);
    }

    @Test
    void returnsNotFoundForMissingDialog() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DialogStore.DialogNotFoundException(id.toString())).when(agents).deleteDialog(id);

        mvc.perform(delete("/api/dialogs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Dialog not found: " + id));
    }

    @Test
    void updatesOnlyProfileSelection() throws Exception {
        UUID dialogId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        mvc.perform(put("/api/dialogs/{id}/profile-selection", dialogId).contentType("application/json")
                        .content("{\"profileId\":\"" + profileId + "\"}"))
                .andExpect(status().isOk());

        verify(store).updateProfileSelection(dialogId.toString(), profileId);
    }

    @Test
    void updatesAndClearsOnlyTaskSelection() throws Exception {
        UUID dialogId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        mvc.perform(put("/api/dialogs/{id}/task-selection", dialogId).contentType("application/json")
                        .content("{\"taskId\":\"" + taskId + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/dialogs/{id}/task-selection", dialogId).contentType("application/json")
                        .content("{\"taskId\":null}"))
                .andExpect(status().isOk());

        verify(store).updateTaskSelection(dialogId.toString(), taskId);
        verify(store).updateTaskSelection(dialogId.toString(), null);
    }
}
