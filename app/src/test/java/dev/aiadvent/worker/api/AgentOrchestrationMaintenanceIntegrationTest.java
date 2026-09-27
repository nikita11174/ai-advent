package dev.aiadvent.worker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.agent.*;
import dev.aiadvent.worker.context.ApproximateTokenEstimator;
import dev.aiadvent.worker.dialog.AgentBranchStore;
import dev.aiadvent.worker.dialog.AgentHistoryStore;
import dev.aiadvent.worker.dialog.ConversationContext;
import dev.aiadvent.worker.dialog.DialogStore;
import dev.aiadvent.worker.invariant.Invariant;
import dev.aiadvent.worker.invariant.InvariantScope;
import dev.aiadvent.worker.invariant.InvariantService;
import dev.aiadvent.worker.mcp.OrchestrationTools;
import dev.aiadvent.worker.memory.*;
import dev.aiadvent.worker.model.*;
import dev.aiadvent.worker.profile.ProfileService;
import dev.aiadvent.worker.task.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
@Import({AgentDialogService.class, AgentModelCatalog.class, ApproximateTokenEstimator.class,
        InvariantGuard.class, ConversationSummaryService.class})
class AgentOrchestrationMaintenanceIntegrationTest {
    interface NativeExecutor extends AgentModelExecutor, ToolCapableModelExecutor { }

    @Autowired private MockMvc mvc;
    @MockitoBean(name = "deepSeekAgentModelExecutor") private NativeExecutor model;
    @MockitoBean(name = "openAiAgentModelExecutor") private AgentModelExecutor openAi;
    @MockitoBean private DialogStore dialogs;
    @MockitoBean private AgentHistoryStore histories;
    @MockitoBean private AgentSummaryStore summaries;
    @Autowired private ConversationSummaryService summaryService;
    @MockitoBean private StickyFactsStore factsStore;
    @MockitoBean private StickyFactsService factsService;
    @MockitoBean private AgentBranchStore branches;
    @MockitoBean private AgentMemoryStore memories;
    @MockitoBean private ProfileService profiles;
    @MockitoBean private TaskService tasks;
    @MockitoBean private InvariantService invariants;
    @MockitoBean private OrchestrationTools tools;

    private UUID dialogId;
    private Invariant rule;
    private final TokenMetrics factsMetrics = new TokenMetrics(1, 2, 41, null);
    private boolean summaryRuns;

    @BeforeEach
    void setup() throws Exception {
        dialogId = UUID.randomUUID();
        rule = new Invariant(UUID.randomUUID(), InvariantScope.USER, null, "Persistence", "Keep PostgreSQL.");
        when(dialogs.load(dialogId.toString())).thenReturn(new DialogStore.DialogDocument(dialogId.toString(),
                "Dialog", Instant.EPOCH, Instant.EPOCH, new ObjectMapper().createObjectNode()));
        when(histories.load(dialogId)).thenReturn(Optional.of(List.of(
                new ConversationContext.Message("system", "base"),
                new ConversationContext.Message("user", "old"),
                new ConversationContext.Message("assistant", "answer"))));
        when(memories.load(eq(dialogId), nullable(UUID.class))).thenReturn(AgentMemory.Snapshot.empty(null));
        when(invariants.effective(nullable(UUID.class))).thenReturn(List.of(rule));
        when(tools.ready()).thenReturn(true);
        when(tools.server("git_repository_status")).thenReturn("workspace");
        when(tools.server("find_allowed_tests")).thenReturn("verification");
        when(tools.execute(any())).thenAnswer(invocation -> {
            var call = invocation.getArgument(0, ToolCapableModelExecutor.ToolRequest.class);
            return new ToolCapableModelExecutor.ToolResult(call.name(), new ObjectMapper().readTree(
                    call.name().equals("find_allowed_tests") ? "{\"checks\":[]}" : "{\"status\":\"SUCCESS\"}"));
        });
        var prepared = mock(ToolCapableModelExecutor.PreparedToolTurn.class);
        var continuation = mock(ToolCapableModelExecutor.PreparedContinuation.class);
        var state = mock(ToolCapableModelExecutor.ToolContinuation.class);
        var request = new ToolCapableModelExecutor.ToolRequest("git_repository_status",
                new ObjectMapper().readTree("{\"includeChangeCounts\":false}"));
        when(model.prepareChoiceTurn(any(), anyList())).thenReturn(prepared);
        when(prepared.estimatedInputTokens()).thenReturn(10L);
        when(model.beginToolTurn(prepared)).thenReturn(new ToolCapableModelExecutor.ToolRequestStep(request, state, null));
        when(model.prepareContinuation(any(), any())).thenReturn(continuation);
        when(model.continueChoiceTurn(continuation)).thenReturn(new ToolCapableModelExecutor.FinalAnswer("candidate", null));
    }

    @Test
    void summaryConflictRetainsOnlyProducedMaintenanceMetrics() throws Exception {
        summaryMaintenance();
        guard("CONFLICT");
        request("SUMMARY_RECENT").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVARIANT_CONFLICT"))
                .andExpect(jsonPath("$.invariantId").value(rule.id().toString()))
                .andExpect(jsonPath("$.invariantName").value("Persistence"))
                .andExpect(jsonPath("$.explanation").value("Guard explanation."))
                .andExpect(jsonPath("$.compatibleContinuation").value("Keep PostgreSQL."))
                .andExpect(jsonPath("$.summaryMetrics.responseTokens").value(2))
                .andExpect(jsonPath("$.factsMetrics").doesNotExist())
                .andExpect(jsonPath("$.guardMetrics.responseTokens").isNumber())
                .andExpect(jsonPath("$.orchestrationTrace.steps[0].tool").value("git_repository_status"));
        verify(model).complete(argThat(request -> request.messages().getFirst().content()
                .contains("Summarize the engineering conversation")));
        noCanonicalAnswer();
    }

    @Test
    void stickyUncertainRetainsOnlyProducedMaintenanceMetrics() throws Exception {
        factsMaintenance();
        guard("UNCERTAIN");
        request("STICKY_FACTS").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVARIANT_UNCERTAIN"))
                .andExpect(jsonPath("$.invariantName").doesNotExist())
                .andExpect(jsonPath("$.explanation").value("Guard explanation."))
                .andExpect(jsonPath("$.compatibleContinuation").value("Keep PostgreSQL."))
                .andExpect(jsonPath("$.factsMetrics[0].responseTokens").value(41))
                .andExpect(jsonPath("$.summaryMetrics").doesNotExist())
                .andExpect(jsonPath("$.guardMetrics.responseTokens").isNumber())
                .andExpect(jsonPath("$.orchestrationTrace.steps[0].tool").value("git_repository_status"));
        verify(factsService).update(any(), eq("check"), any(), eq(2), same(model));
        noCanonicalAnswer();
    }

    @Test
    void summaryGuardFailureRetainsMaintenanceAndGuardMetrics() throws Exception {
        summaryMaintenance();
        guard("INVALID");
        request("SUMMARY_RECENT").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INVARIANT_CHECK_FAILED"))
                .andExpect(jsonPath("$.summaryMetrics.responseTokens").value(2))
                .andExpect(jsonPath("$.factsMetrics").doesNotExist())
                .andExpect(jsonPath("$.guardMetrics.responseTokens").isNumber())
                .andExpect(jsonPath("$.orchestrationTrace.steps[0].tool").value("git_repository_status"));
        noCanonicalAnswer();
    }

    @Test
    void stickyGuardFailureRetainsMaintenanceAndGuardMetrics() throws Exception {
        factsMaintenance();
        guard("INVALID");
        request("STICKY_FACTS").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INVARIANT_CHECK_FAILED"))
                .andExpect(jsonPath("$.factsMetrics[0].responseTokens").value(41))
                .andExpect(jsonPath("$.summaryMetrics").doesNotExist())
                .andExpect(jsonPath("$.guardMetrics.responseTokens").isNumber())
                .andExpect(jsonPath("$.orchestrationTrace.steps[0].tool").value("git_repository_status"));
        noCanonicalAnswer();
    }

    @Test
    void fullModeDoesNotInventMaintenanceMetrics() throws Exception {
        guard("CONFLICT");
        request("FULL").andExpect(status().isConflict())
                .andExpect(jsonPath("$.summaryMetrics").doesNotExist())
                .andExpect(jsonPath("$.factsMetrics").doesNotExist())
                .andExpect(jsonPath("$.guardMetrics.responseTokens").isNumber())
                .andExpect(jsonPath("$.orchestrationTrace.steps[0].tool").value("git_repository_status"));
        noCanonicalAnswer();
    }

    @Test
    void unsupportedProviderFailsBeforeAnyMcpExecution() throws Exception {
        mvc.perform(post("/api/dialogs/{id}/agent/messages", dialogId).contentType("application/json")
                        .content("{\"input\":\"check\",\"agentModelKey\":\"STRONG\",\"useMcpOrchestration\":true}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("TOOL_CAPABILITY_UNSUPPORTED"))
                .andExpect(jsonPath("$.orchestrationTrace.steps").isEmpty());
        verify(tools, never()).execute(any());
        verify(model, never()).prepareChoiceTurn(any(), anyList());
        verify(openAi, never()).complete(any());
        verify(histories, never()).save(any(), anyList());
        verify(tasks, never()).apply(any(), any(), anyLong());
    }

    @Test
    void sixthToolCallFailsWithoutCanonicalAnswer() throws Exception {
        var state = mock(ToolCapableModelExecutor.ToolContinuation.class);
        var steps = new ToolCapableModelExecutor.ToolRequestStep[6];
        for (int i = 0; i < steps.length; i++) steps[i] = new ToolCapableModelExecutor.ToolRequestStep(
                new ToolCapableModelExecutor.ToolRequest("find_allowed_tests",
                        new ObjectMapper().readTree("{\"concept\":\"c" + i + "\"}")), state, null);
        when(model.beginToolTurn(any())).thenReturn(steps[0]);
        when(model.continueChoiceTurn(any())).thenReturn(steps[1], steps[2], steps[3], steps[4], steps[5]);

        request("FULL").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("TOOL_STEP_LIMIT"))
                .andExpect(jsonPath("$.orchestrationTrace.steps.length()").value(5));
        verify(tools, times(5)).execute(any());
        verify(histories, never()).save(any(), anyList());
        verify(tasks, never()).apply(any(), any(), anyLong());
    }

    @Test
    void providerFailureAfterTwoToolsLeavesOnlyTechnicalTrace() throws Exception {
        var second = new ToolCapableModelExecutor.ToolRequestStep(
                new ToolCapableModelExecutor.ToolRequest("find_allowed_tests",
                        new ObjectMapper().readTree("{\"concept\":\"branch\"}")),
                mock(ToolCapableModelExecutor.ToolContinuation.class), null);
        when(model.continueChoiceTurn(any())).thenReturn(second)
                .thenThrow(new ModelExecutionException("provider unavailable"));

        request("FULL").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("provider unavailable"))
                .andExpect(jsonPath("$.orchestrationTrace.steps.length()").value(2))
                .andExpect(jsonPath("$.analysis").doesNotExist());
        verify(tools, times(2)).execute(any());
        verify(histories, never()).save(any(), anyList());
        verify(tasks, never()).apply(any(), any(), anyLong());
    }

    private void summaryMaintenance() {
        summaryRuns = true;
    }

    private void factsMaintenance() throws Exception {
        when(factsStore.load(dialogId)).thenReturn(Optional.of(new StickyFacts(1, Map.of())));
        when(factsService.update(any(), eq("check"), any(), eq(2), same(model)))
                .thenReturn(new StickyFactsGeneration(new StickyFacts(2, Map.of("project", "ai-advent")), factsMetrics));
    }

    private void guard(String decision) throws Exception {
        String result = decision.equals("INVALID") ? "invalid json" : """
                {"decision":"%s","invariantId":%s,"explanation":"Guard explanation.","compatibleContinuation":"Keep PostgreSQL."}
                """.formatted(decision, decision.equals("CONFLICT") ? "\"" + rule.id() + "\"" : "null");
        if (summaryRuns) when(model.complete(any())).thenReturn(
                new AgentModelExecutor.Completion("summary", null), new AgentModelExecutor.Completion(result, null));
        else when(model.complete(any())).thenReturn(new AgentModelExecutor.Completion(result, null));
    }

    private org.springframework.test.web.servlet.ResultActions request(String mode) throws Exception {
        return mvc.perform(post("/api/dialogs/{id}/agent/messages", dialogId).contentType("application/json")
                .content("{\"input\":\"check\",\"contextMode\":\"" + mode + "\",\"recentMessageCount\":1,"
                        + "\"useMcpOrchestration\":true}"));
    }

    private void noCanonicalAnswer() throws Exception {
        verify(tools).execute(any());
        verify(histories, never()).save(any(), anyList());
        verify(tasks, never()).apply(any(), any(), anyLong());
    }
}
