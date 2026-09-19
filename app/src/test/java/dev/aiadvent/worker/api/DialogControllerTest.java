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
}
