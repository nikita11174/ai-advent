package dev.aiadvent.worker;

import dev.aiadvent.worker.dialog.AgentBranchStore;
import dev.aiadvent.worker.dialog.AgentHistoryStore;
import dev.aiadvent.worker.dialog.DialogStore;
import dev.aiadvent.worker.memory.AgentMemory;
import dev.aiadvent.worker.memory.AgentMemoryStore;
import dev.aiadvent.worker.memory.AgentSummaryStore;
import dev.aiadvent.worker.memory.StickyFactsStore;
import dev.aiadvent.worker.context.ApproximateTokenEstimator;
import dev.aiadvent.worker.model.ModelTestFixtures;

import dev.aiadvent.worker.model.AgentModelCatalog;
import dev.aiadvent.worker.model.AgentModelExecutor;
import dev.aiadvent.worker.model.OpenAiResponsesClient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentMemoryController.class)
@Import({AgentDialogService.class, AgentModelCatalog.class, ModelTestFixtures.class})
class AgentMemoryControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DialogStore dialogs;
    @MockitoBean(name = "deepSeekAgentModelExecutor")
    private AgentModelExecutor client;
    @MockitoBean
    private OpenAiResponsesClient openAi;
    @MockitoBean
    private AgentHistoryStore histories;
    @MockitoBean
    private ApproximateTokenEstimator tokenEstimator;
    @MockitoBean
    private AgentSummaryStore summaries;
    @MockitoBean
    private ConversationSummaryService summaryService;
    @MockitoBean
    private StickyFactsStore factsStore;
    @MockitoBean
    private StickyFactsService factsService;
    @MockitoBean
    private AgentBranchStore branches;
    @MockitoBean
    private AgentMemoryStore memories;

    @Test
    void readsAndUpsertsExactMemoryWithoutCallingProvider() throws Exception {
        UUID dialogId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        var snapshot = new AgentMemory.Snapshot(taskId, Map.of("codeword", "SATURN"),
                Map.of("database", "PostgreSQL"), Map.of("language", "Java"));
        when(memories.load(dialogId, taskId)).thenReturn(snapshot);
        when(memories.upsert(dialogId, taskId, AgentMemory.Scope.WORKING, "database", "PostgreSQL"))
                .thenReturn(snapshot);

        mvc.perform(get("/api/dialogs/{id}/agent/memory", dialogId).param("taskId", taskId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.shortTerm.codeword").value("SATURN"));
        mvc.perform(put("/api/dialogs/{id}/agent/memory/WORKING", dialogId)
                        .contentType("application/json")
                        .content("{\"taskId\":\"" + taskId + "\",\"key\":\"database\",\"value\":\"PostgreSQL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working.database").value("PostgreSQL"));

        verify(dialogs, times(2)).load(dialogId.toString());
        verifyNoInteractions(client);
    }

    @Test
    void rejectsInvalidRequestsAndUnknownDialogBeforeMemoryAccess() throws Exception {
        UUID dialogId = UUID.randomUUID();
        mvc.perform(put("/api/dialogs/{id}/agent/memory/WORKING", dialogId)
                        .contentType("application/json").content("{\"key\":\"key\",\"value\":\"value\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/dialogs/{id}/agent/memory/UNKNOWN", dialogId)
                        .contentType("application/json").content("{\"key\":\"key\",\"value\":\"value\"}"))
                .andExpect(status().isBadRequest());
        reset(dialogs, memories);
        when(dialogs.load(dialogId.toString())).thenThrow(new DialogStore.DialogNotFoundException(dialogId.toString()));

        mvc.perform(get("/api/dialogs/{id}/agent/memory", dialogId)).andExpect(status().isNotFound());

        verifyNoInteractions(memories, client);
    }
}
