package dev.aiadvent.worker;

import dev.aiadvent.worker.dialog.AgentBranchStore;
import dev.aiadvent.worker.dialog.AgentHistoryStore;
import dev.aiadvent.worker.dialog.DialogStore;
import dev.aiadvent.worker.memory.AgentMemoryStore;
import dev.aiadvent.worker.memory.AgentMemory;
import dev.aiadvent.worker.memory.AgentSummaryStore;
import dev.aiadvent.worker.memory.StickyFacts;
import dev.aiadvent.worker.context.ApproximateTokenEstimator;
import dev.aiadvent.worker.context.ContextMode;
import dev.aiadvent.worker.context.ContextLimitExceededException;
import dev.aiadvent.worker.memory.StickyFactsStore;
import dev.aiadvent.worker.model.ModelTestFixtures;

import dev.aiadvent.worker.model.AgentModelCatalog;
import dev.aiadvent.worker.model.AgentModelExecutor;
import dev.aiadvent.worker.model.AgentModelRequest;
import dev.aiadvent.worker.model.ModelExecutionException;
import dev.aiadvent.worker.model.OpenAiResponsesClient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
@Import({AgentDialogService.class, AgentModelCatalog.class, ModelTestFixtures.class})
@TestPropertySource(properties = "mentor.agent.context-token-limit=1")
class AgentControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DialogStore store;
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

    @BeforeEach
    void emptyMemoryByDefault() throws Exception {
        when(memories.load(any(UUID.class), nullable(UUID.class)))
                .thenAnswer(call -> AgentMemory.Snapshot.empty(call.getArgument(1)));
    }

    @Test
    void rejectsUnknownAgentModelBeforeProviderOrStorage() throws Exception {
        mvc.perform(post("/api/dialogs/" + UUID.randomUUID() + "/agent/messages").contentType("application/json")
                .content("{\"input\":\"hello\",\"agentModelKey\":\"unknown\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Неизвестная модель агента."));
        verifyNoInteractions(client, openAi, store);
    }

    @Test
    void returnsAnalysisAndRejectsAnOverlappingRequest() throws Exception {
        String route = "/api/dialogs/" + UUID.randomUUID() + "/agent/messages";
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.complete(any(AgentModelRequest.class))).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new AgentModelExecutor.Completion("answer", null);
        });
        var first = new FutureTask<>(() -> mvc.perform(post(route).contentType("application/json")
                .content("{\"input\":\"first\"}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"analysis\":\"answer\"}")));
        Thread thread = Thread.ofPlatform().start(first);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            mvc.perform(post(route).contentType("application/json").content("{\"input\":\"second\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").isNotEmpty());
        } finally {
            release.countDown();
            thread.join(5000);
        }
        first.get(5, TimeUnit.SECONDS);
        verify(client).complete(any(AgentModelRequest.class));
    }

    @Test
    void rejectsInvalidAndUnknownDialogsBeforeCallingProvider() throws Exception {
        String id = UUID.randomUUID().toString();
        String route = "/api/dialogs/" + id + "/agent/messages";
        for (String body : new String[]{"{}", "{\"input\":null}", "{\"input\":\"  \"}"}) {
            mvc.perform(post(route).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/dialogs/invalid/agent/messages").contentType("application/json")
                .content("{\"input\":\"hello\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(store, client);
        when(store.load(id)).thenThrow(new DialogStore.DialogNotFoundException(id));
        mvc.perform(post(route).contentType("application/json").content("{\"input\":\"hello\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").isNotEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void mapsStorageAndProviderFailures() throws Exception {
        String missing = UUID.randomUUID().toString();
        when(store.load(missing)).thenThrow(new IOException("unavailable"));
        mvc.perform(post("/api/dialogs/" + missing + "/agent/messages").contentType("application/json")
                .content("{\"input\":\"hello\"}"))
                .andExpect(status().isInternalServerError());
        verifyNoInteractions(client);
        when(client.complete(any(AgentModelRequest.class))).thenThrow(new ModelExecutionException("Unavailable"));
        mvc.perform(post("/api/dialogs/" + UUID.randomUUID() + "/agent/messages").contentType("application/json")
                .content("{\"input\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("{\"error\":\"Unavailable\",\"rawResponse\":null}"));
    }

    @Test
    void mapsAnEstimatedContextOverflowBeforeCallingTheProvider() throws Exception {
        when(tokenEstimator.estimateMessagesWithinLimit(anyList(), eq(1)))
                .thenThrow(new ContextLimitExceededException(2, 1));

        mvc.perform(post("/api/dialogs/" + UUID.randomUUID() + "/agent/messages").contentType("application/json")
                .content("{\"input\":\"hello\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("Estimated context limit exceeded: 2 > 1."));

        verifyNoInteractions(client);
        verify(histories).load(any());
        verify(histories, never()).save(any(), anyList());
    }

    @Test
    void exposesCompletedFactsMaintenanceMetricsWhenTheMainCallFails() throws Exception {
        UUID dialogId = UUID.randomUUID();
        when(factsStore.load(dialogId)).thenReturn(java.util.Optional.empty());
        var maintenance = new TokenMetrics(2, 3, 4, null);
        when(factsService.update(any(), eq("hello"), any(), eq(1), any()))
                .thenReturn(new StickyFactsGeneration(new StickyFacts(1, java.util.Map.of("project", "Helios")), maintenance));
        when(client.complete(any(AgentModelRequest.class))).thenThrow(new ModelExecutionException("Unavailable"));

        mvc.perform(post("/api/dialogs/" + dialogId + "/agent/messages").contentType("application/json")
                        .content("{\"input\":\"hello\",\"contextMode\":\"STICKY_FACTS\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Unavailable"))
                .andExpect(jsonPath("$.factsMetrics[0].contextTokens").value(3));
    }

    @Test
    void mapsTaskIdAndReturnsValueFreeMemoryEvidence() throws Exception {
        UUID dialogId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(memories.load(dialogId, taskId)).thenReturn(new AgentMemory.Snapshot(taskId,
                java.util.Map.of("codeword", "SATURN"), java.util.Map.of(), java.util.Map.of()));
        when(client.complete(any(AgentModelRequest.class)))
                .thenReturn(new AgentModelExecutor.Completion("answer", null));

        String response = mvc.perform(post("/api/dialogs/{id}/agent/messages", dialogId)
                        .contentType("application/json")
                        .content("{\"input\":\"hello\",\"taskId\":\"" + taskId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextMetadata.memoryUsed[0].scope").value("SHORT_TERM"))
                .andExpect(jsonPath("$.contextMetadata.memoryUsed[0].keys[0]").value("codeword"))
                .andExpect(jsonPath("$.contextMetadata.memoryUsed[0].entryCount").value(1))
                .andReturn().getResponse().getContentAsString();

        assertTrue(!response.contains("SATURN"));
        verify(memories).load(dialogId, taskId);
    }
}
