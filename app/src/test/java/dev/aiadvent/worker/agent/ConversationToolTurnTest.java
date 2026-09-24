package dev.aiadvent.worker.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.context.*;
import dev.aiadvent.worker.dialog.*;
import dev.aiadvent.worker.memory.*;
import dev.aiadvent.worker.model.*;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.*;
import dev.aiadvent.worker.invariant.Invariant;
import dev.aiadvent.worker.invariant.InvariantScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationToolTurnTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ToolCapableModelExecutor nativeModel = mock(ToolCapableModelExecutor.class,
            withSettings().extraInterfaces(AgentModelExecutor.class));
    private final AgentModelExecutor executor = (AgentModelExecutor) nativeModel;
    private final ToolExecutor tool = mock(ToolExecutor.class, withSettings().extraInterfaces(MonitorReadExecutor.class));
    private final AgentHistoryStore histories = mock(AgentHistoryStore.class);
    private final ConversationContext context = new ConversationContext(AgentConfig.defaults().systemPrompt());
    private final PreparedToolTurn first = mock(PreparedToolTurn.class);
    private final PreparedContinuation second = mock(PreparedContinuation.class);
    private final ToolContinuation continuation = mock(ToolContinuation.class);

    private ConversationAgent agent(AgentConfig config) {
        return new ConversationAgent(UUID.randomUUID(), config, context, executor, histories,
                new ApproximateTokenEstimator(), mock(AgentSummaryStore.class), mock(ConversationSummaryService.class),
                mock(StickyFactsStore.class), mock(StickyFactsService.class), new FullContextPolicy(),
                new SummaryRecentContextPolicy(), new SlidingWindowContextPolicy(), new StickyFactsContextPolicy(),
                mock(AgentBranchStore.class), null, null, tool);
    }

    private void setup() throws Exception {
        when(tool.enabled()).thenReturn(true);
        when(nativeModel.prepareToolTurn(any(), any())).thenReturn(first);
        when(nativeModel.prepareContinuation(any(), any())).thenReturn(second);
        when(first.estimatedInputTokens()).thenReturn(10L);
        when(second.estimatedInputTokens()).thenReturn(20L);
        when(nativeModel.beginToolTurn(first)).thenReturn(new ToolRequestStep(
                new ToolRequest("git_repository_status", json.readTree("{\"includeChangeCounts\":true}")),
                continuation, null));
        when(tool.execute(any())).thenReturn(new ToolResult("git_repository_status", json.readTree("{\"dirty\":false}")));
        when(nativeModel.continueToolTurn(second)).thenReturn(new FinalAnswer("final answer", null));
    }

    private AgentReply required(ConversationAgent agent) throws Exception {
        return agent.replyWithTool("status", ContextMode.FULL, 4, AgentMemory.Snapshot.empty(null), executor,
                AgentConfig.defaults(), null, null, List.of(), List.of(), true);
    }

    @Test void ordinaryPathRemainsTextOnly() throws Exception {
        setup();
        when(executor.complete(any())).thenReturn(new AgentModelExecutor.Completion("ordinary", null));
        assertEquals("ordinary", agent(AgentConfig.defaults()).reply("status").analysis());
        verify(nativeModel, never()).prepareToolTurn(any(), any());
        verify(tool, never()).execute(any());
    }

    @Test void explicitMonitorReadExposesOnlyReadAndCommitsCanonicalPair() throws Exception {
        setup();
        var monitor = (MonitorReadExecutor) tool;
        when(monitor.monitorEnabled()).thenReturn(true);
        when(nativeModel.beginToolTurn(first)).thenReturn(new ToolRequestStep(
                new ToolRequest("get_repository_monitor_summary", json.readTree("{}")), continuation, null));
        when(monitor.readMonitor(any())).thenReturn(new ToolResult("get_repository_monitor_summary",
                json.readTree("{\"latestDigest\":{\"text\":\"local digest\"}}")));
        var agent = agent(AgentConfig.defaults());
        var reply = agent.replyWithTool("Explain the monitor", ContextMode.FULL, 4,
                AgentMemory.Snapshot.empty(null), executor, AgentConfig.defaults(), null, null,
                List.of(), List.of(), false, true);
        assertEquals("final answer", reply.analysis());
        var definition = org.mockito.ArgumentCaptor.forClass(ToolDefinition.class);
        verify(nativeModel).prepareToolTurn(any(), definition.capture());
        assertEquals("get_repository_monitor_summary", definition.getValue().name());
        assertEquals(0, definition.getValue().inputSchema().path("properties").size());
        verify(monitor).readMonitor(any());
        verify(tool, never()).execute(any());
        assertEquals(3, context.snapshot().size());
        assertFalse(context.snapshot().toString().contains("local digest"));

        when(nativeModel.beginToolTurn(first)).thenReturn(new ToolRequestStep(
                new ToolRequest("start_repository_monitor", json.readTree("{}")), continuation, null));
        assertEquals("TOOL_NOT_ALLOWED", assertThrows(ToolTurnException.class,
                () -> agent.replyWithTool("start it", ContextMode.FULL, 4, AgentMemory.Snapshot.empty(null),
                        executor, AgentConfig.defaults(), null, null, List.of(), List.of(), false, true)).getMessage());
        verify(monitor, times(1)).readMonitor(any());
    }

    @Test void successfulTurnCommitsOnlyUserAndFinalAndCanReuseExecutor() throws Exception {
        setup();
        var agent = agent(AgentConfig.defaults());
        assertEquals("SUCCESS", required(agent).toolTrace().toolStatus());
        assertEquals("SUCCESS", required(agent).toolTrace().turnStatus());
        assertEquals(List.of(new ConversationContext.Message("system", AgentConfig.defaults().systemPrompt()),
                new ConversationContext.Message("user", "status"), new ConversationContext.Message("assistant", "final answer"),
                new ConversationContext.Message("user", "status"), new ConversationContext.Message("assistant", "final answer")),
                context.snapshot());
        verify(tool, times(2)).execute(any());
        verify(histories, times(2)).save(any(), anyList());
        verify(executor, never()).complete(any());
    }

    @Test void unsupportedAndDirectAnswerRejectWithoutToolOrCommit() throws Exception {
        setup();
        var plain = mock(AgentModelExecutor.class);
        var agent = agent(AgentConfig.defaults());
        var unsupported = assertThrows(ToolTurnException.class, () -> agent.replyWithTool("status", ContextMode.FULL,
                4, AgentMemory.Snapshot.empty(null), plain, AgentConfig.defaults(), null, null, List.of(), List.of(), true));
        assertEquals("TOOL_CAPABILITY_UNSUPPORTED", unsupported.getMessage());
        when(nativeModel.beginToolTurn(first)).thenReturn(new FinalAnswer("guess", null));
        assertEquals("TOOL_REQUIRED", assertThrows(ToolTurnException.class, () -> required(agent)).getMessage());
        verify(tool, never()).execute(any());
        verify(histories, never()).save(any(), anyList());
    }

    @Test void invalidRequestsAndMcpFailureDoNotCommit() throws Exception {
        setup();
        var agent = agent(AgentConfig.defaults());
        for (String args : List.of("{}", "{\"includeChangeCounts\":\"true\"}",
                "{\"includeChangeCounts\":true,\"path\":\".\"}")) {
            when(nativeModel.beginToolTurn(first)).thenReturn(new ToolRequestStep(
                    new ToolRequest("git_repository_status", json.readTree(args)), continuation, null));
            assertEquals("INVALID_TOOL_ARGUMENTS", assertThrows(ToolTurnException.class, () -> required(agent)).getMessage());
        }
        when(nativeModel.beginToolTurn(first)).thenReturn(new ToolRequestStep(
                new ToolRequest("other", json.readTree("{\"includeChangeCounts\":true}")), continuation, null));
        assertEquals("TOOL_NOT_ALLOWED", assertThrows(ToolTurnException.class, () -> required(agent)).getMessage());
        verify(tool, never()).execute(any());
        when(nativeModel.beginToolTurn(first)).thenReturn(new ToolRequestStep(
                new ToolRequest("git_repository_status", json.readTree("{\"includeChangeCounts\":true}")), continuation, null));
        when(tool.execute(any())).thenThrow(new IllegalStateException("MCP_CALL_FAILED"));
        assertEquals("MCP_CALL_FAILED", assertThrows(ToolTurnException.class, () -> required(agent)).getMessage());
        verify(histories, never()).save(any(), anyList());
    }

    @Test void multipleOrMalformedNativeCallsNeverReachExecutor() throws Exception {
        setup();
        var agent = agent(AgentConfig.defaults());
        when(nativeModel.beginToolTurn(first)).thenThrow(new ModelExecutionException("TOOL_CALL_LIMIT"),
                new ModelExecutionException("MALFORMED_TOOL_RESPONSE"));
        assertEquals("TOOL_CALL_LIMIT", assertThrows(ToolTurnException.class, () -> required(agent)).getMessage());
        assertEquals("MALFORMED_TOOL_RESPONSE", assertThrows(ToolTurnException.class, () -> required(agent)).getMessage());
        verify(tool, never()).execute(any());
        verify(histories, never()).save(any(), anyList());
    }

    @Test void providerFailureTextIsNotAnApplicationErrorCode() throws Exception {
        setup();
        when(nativeModel.beginToolTurn(first)).thenThrow(new ModelExecutionException("private provider detail"));
        var failure = assertThrows(ToolTurnException.class, () -> required(agent(AgentConfig.defaults())));
        assertEquals("MODEL_EXECUTION_FAILED", failure.getMessage());
        assertEquals("MODEL_EXECUTION_FAILED", failure.trace().code());
        verify(tool, never()).execute(any());
        verify(histories, never()).save(any(), anyList());
    }

    @Test void secondBudgetFailureRecordsSuccessfulToolWithoutCommit() throws Exception {
        setup();
        when(second.estimatedInputTokens()).thenReturn(101L);
        AgentConfig base = AgentConfig.defaults();
        var limited = new AgentConfig(base.model(), base.systemPrompt(), null, null, 100);
        var agent = agent(limited);
        var failure = assertThrows(ToolTurnException.class, () -> agent.replyWithTool("status", ContextMode.FULL,
                4, AgentMemory.Snapshot.empty(null), executor, limited, null, null, List.of(), List.of(), true));
        assertEquals("CONTEXT_LIMIT", failure.getMessage());
        assertEquals("SUCCESS", failure.trace().toolStatus());
        assertEquals("FAILED", failure.trace().turnStatus());
        verify(nativeModel, never()).continueToolTurn(any());
        verify(histories, never()).save(any(), anyList());
    }

    @Test void repeatedToolRequestInContinuationDoesNotCommit() throws Exception {
        setup();
        when(nativeModel.continueToolTurn(second)).thenThrow(new ModelExecutionException("TOOL_CALL_LIMIT"));
        var failure = assertThrows(ToolTurnException.class, () -> required(agent(AgentConfig.defaults())));
        assertEquals("TOOL_CALL_LIMIT", failure.getMessage());
        assertEquals("SUCCESS", failure.trace().toolStatus());
        verify(histories, never()).save(any(), anyList());
    }

    @Test void invariantConflictAfterSuccessfulToolRejectsCanonicalCommit() throws Exception {
        setup();
        var guard = mock(InvariantGuard.class);
        var invariant = new Invariant(UUID.randomUUID(), InvariantScope.USER, null, "Rule", "Do not guess");
        when(guard.assess(any(), any(), anyList(), any(), any())).thenReturn(new InvariantGuard.Assessment(
                new InvariantGuard.Outcome(InvariantGuard.Decision.CONFLICT, invariant.id(), "conflict", "safe"), null));
        var config = AgentConfig.defaults();
        var agent = new ConversationAgent(UUID.randomUUID(), config, context, executor, histories,
                new ApproximateTokenEstimator(), mock(AgentSummaryStore.class), mock(ConversationSummaryService.class),
                mock(StickyFactsStore.class), mock(StickyFactsService.class), new FullContextPolicy(),
                new SummaryRecentContextPolicy(), new SlidingWindowContextPolicy(), new StickyFactsContextPolicy(),
                mock(AgentBranchStore.class), null, guard, tool);
        var failure = assertThrows(ToolTurnException.class, () -> agent.replyWithTool("status", ContextMode.FULL,
                4, AgentMemory.Snapshot.empty(null), executor, config, null, null, List.of(invariant), List.of(), true));
        assertEquals("INVARIANT_CONFLICT", failure.getMessage());
        assertEquals("SUCCESS", failure.trace().toolStatus());
        assertEquals("REJECTED", failure.trace().turnStatus());
        assertEquals(1, context.snapshot().size());
        verify(histories, never()).save(any(), anyList());
    }
}
