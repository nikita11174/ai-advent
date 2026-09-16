package dev.aiadvent.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentBranchController.class)
class AgentBranchControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AgentDialogService agents;

    @Test
    void exposesCheckpointBranchCreationAndListing() throws Exception {
        UUID dialogId = UUID.randomUUID();
        var checkpoint = new AgentBranchStore.Checkpoint("checkpoint", List.of(
                new ConversationContext.Message("system", "instruction")), List.of());
        var branch = new AgentBranchStore.Branch("branch", checkpoint.id(), checkpoint.baseHistory());
        when(agents.createCheckpoint(eq(dialogId), eq(null))).thenReturn(checkpoint);
        when(agents.createBranch(dialogId, checkpoint.id())).thenReturn(branch);
        when(agents.branches(dialogId)).thenReturn(List.of(branch));
        when(agents.checkpoints(dialogId)).thenReturn(List.of(checkpoint));

        mvc.perform(post("/api/dialogs/{id}/agent/checkpoints", dialogId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("checkpoint"));
        mvc.perform(post("/api/dialogs/{id}/agent/checkpoints/{checkpointId}/branches", dialogId, checkpoint.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("branch"));
        mvc.perform(get("/api/dialogs/{id}/agent/branches", dialogId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].checkpointId").value("checkpoint"));
        mvc.perform(get("/api/dialogs/{id}/agent/checkpoints", dialogId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("checkpoint"));
    }

    @Test
    void mapsUnknownBranchToNotFound() throws Exception {
        UUID dialogId = UUID.randomUUID();
        when(agents.createBranch(eq(dialogId), any()))
                .thenThrow(new AgentBranchStore.BranchNotFoundException("Branch not found"));

        mvc.perform(post("/api/dialogs/{id}/agent/checkpoints/missing/branches", dialogId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Branch not found"));
    }
}
