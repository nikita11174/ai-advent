package dev.aiadvent.worker.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.context.ApproximateTokenEstimator;
import dev.aiadvent.worker.context.ContextMode;
import dev.aiadvent.worker.context.FullContextPolicy;
import dev.aiadvent.worker.context.SlidingWindowContextPolicy;
import dev.aiadvent.worker.context.StickyFactsContextPolicy;
import dev.aiadvent.worker.context.SummaryRecentContextPolicy;
import dev.aiadvent.worker.dialog.AgentBranchStore;
import dev.aiadvent.worker.dialog.AgentHistoryStore;
import dev.aiadvent.worker.dialog.ConversationContext;
import dev.aiadvent.worker.invariant.Invariant;
import dev.aiadvent.worker.invariant.InvariantScope;
import dev.aiadvent.worker.memory.AgentMemory;
import dev.aiadvent.worker.memory.AgentSummaryStore;
import dev.aiadvent.worker.memory.ConversationSummary;
import dev.aiadvent.worker.memory.StickyFactsStore;
import dev.aiadvent.worker.model.AgentModelExecutor;
import dev.aiadvent.worker.model.AgentModelRequest;
import dev.aiadvent.worker.profile.Profile;
import dev.aiadvent.worker.task.Task;
import dev.aiadvent.worker.task.TaskStage;
import dev.aiadvent.worker.task.TaskState;
import dev.aiadvent.worker.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvariantContextIntegrationTest {
    @Test
    void projectsInvariantsAfterProfileAndTaskAndAllowsCandidate() throws Exception {
        AgentModelExecutor executor = mock(AgentModelExecutor.class);
        when(executor.complete(any())).thenReturn(completion("candidate"),
                completion("{\"decision\":\"ALLOW\",\"invariantId\":null,\"explanation\":\"\",\"compatibleContinuation\":\"\"}"));
        AgentHistoryStore histories = mock(AgentHistoryStore.class);
        Profile profile = new Profile(UUID.randomUUID(), "Profile", "PROFILE", "Brief", "Markdown");
        Task task = task();
        Invariant invariant = invariant(task.id());

        agent(executor, histories).reply("request", ContextMode.FULL, 4, AgentMemory.Snapshot.empty(task.id()), executor,
                config(), profile, task, List.of(invariant));

        ArgumentCaptor<AgentModelRequest> requests = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(executor, org.mockito.Mockito.times(2)).complete(requests.capture());
        List<?> messages = requests.getAllValues().getFirst().messages();
        assertTrueAt(messages.toString(), "base", "PROFILE", "Task ID", "Mandatory application invariants", "request");
        verify(histories).save(any(), any());
    }

    @Test
    void conflictOrUncertainCandidateIsNotPersisted() throws Exception {
        for (String decision : List.of("CONFLICT", "UNCERTAIN")) {
            AgentModelExecutor executor = mock(AgentModelExecutor.class);
            Invariant invariant = invariant(UUID.randomUUID());
            String id = decision.equals("CONFLICT") ? invariant.id().toString() : "null";
            when(executor.complete(any())).thenReturn(completion("replace PostgreSQL with Redis"), completion("""
                    {"decision":"%s","invariantId":%s,"explanation":"Violates persistence rule.","compatibleContinuation":"Keep PostgreSQL."}"""
                    .formatted(decision, id.equals("null") ? "null" : "\"" + id + "\"")));
            AgentHistoryStore histories = mock(AgentHistoryStore.class);

            assertThrows(InvariantGuard.RejectedCandidateException.class,
                    () -> agent(executor, histories).reply("replace persistence", ContextMode.FULL, 4,
                            AgentMemory.Snapshot.empty(null), executor, config(), null, null, List.of(invariant)));
            verify(histories, never()).save(any(), any());
        }
    }

    @Test
    void malformedGuardResultFailsWithoutPersistingCandidate() throws Exception {
        AgentModelExecutor executor = mock(AgentModelExecutor.class);
        when(executor.complete(any())).thenReturn(completion("candidate"), completion("not json"));
        AgentHistoryStore histories = mock(AgentHistoryStore.class);

        assertThrows(InvariantGuard.GuardFailureException.class,
                () -> agent(executor, histories).reply("request", ContextMode.FULL, 4, AgentMemory.Snapshot.empty(null),
                        executor, config(), null, null, List.of(invariant(null))));
        verify(histories, never()).save(any(), any());
    }

    @Test
    void invalidInvariantReferencesFailClosedWithoutPersistingCandidate() throws Exception {
        for (String result : List.of(
                "{\"decision\":\"ALLOW\",\"invariantId\":\"%s\",\"explanation\":\"\",\"compatibleContinuation\":\"\"}",
                "{\"decision\":\"CONFLICT\",\"invariantId\":\"%s\",\"explanation\":\"Conflict.\",\"compatibleContinuation\":\"Keep PostgreSQL.\"}",
                "{\"decision\":\"UNCERTAIN\",\"invariantId\":\"%s\",\"explanation\":\"Unsure.\",\"compatibleContinuation\":\"Keep PostgreSQL.\"}")) {
            AgentModelExecutor executor = mock(AgentModelExecutor.class);
            Invariant invariant = invariant(null);
            when(executor.complete(any())).thenReturn(completion("candidate"), completion(result.formatted(UUID.randomUUID())));
            AgentHistoryStore histories = mock(AgentHistoryStore.class);

            assertThrows(InvariantGuard.GuardFailureException.class,
                    () -> agent(executor, histories).reply("request", ContextMode.FULL, 4, AgentMemory.Snapshot.empty(null),
                            executor, config(), null, null, List.of(invariant)));
            verify(histories, never()).save(any(), any());
        }
    }

    @Test
    void reportsGuardUsageSeparatelyOnAllowedTurn() throws Exception {
        AgentModelExecutor executor = mock(AgentModelExecutor.class);
        when(executor.complete(any())).thenReturn(completion("candidate"), completion("""
                {"decision":"ALLOW","invariantId":null,"explanation":"","compatibleContinuation":""}"""));

        var reply = agent(executor, mock(AgentHistoryStore.class)).reply("request", ContextMode.FULL, 4,
                AgentMemory.Snapshot.empty(null), executor, config(), null, null, List.of(invariant(null)));

        assertNotNull(reply.guardMetrics());
        assertTrue(reply.guardMetrics().responseTokens() > 0);
    }

    @Test
    void preservesMaintenanceMetricsWhenGuardFails() throws Exception {
        AgentModelExecutor executor = mock(AgentModelExecutor.class);
        when(executor.complete(any())).thenReturn(completion("candidate"), completion("not json"));
        AgentSummaryStore summaries = mock(AgentSummaryStore.class);
        ConversationSummaryService summaryService = mock(ConversationSummaryService.class);
        UUID dialogId = UUID.randomUUID();
        when(summaries.load(dialogId)).thenReturn(java.util.Optional.empty());
        when(summaryService.generate(any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(config()),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.same(executor)))
                .thenReturn(new SummaryGeneration(new ConversationSummary(1, "summary"), new TokenMetrics(1, 2, 3, null)));
        ConversationAgent agent = new ConversationAgent(dialogId, config(), new ConversationContext(List.of(
                new ConversationContext.Message("system", "base"), new ConversationContext.Message("user", "old"),
                new ConversationContext.Message("assistant", "answer"))), executor, mock(AgentHistoryStore.class),
                new ApproximateTokenEstimator(), summaries, summaryService, mock(StickyFactsStore.class), mock(StickyFactsService.class),
                new FullContextPolicy(), new SummaryRecentContextPolicy(), new SlidingWindowContextPolicy(), new StickyFactsContextPolicy(),
                mock(AgentBranchStore.class), null, new InvariantGuard(new ObjectMapper(), new ApproximateTokenEstimator()));

        var failure = assertThrows(ConversationAgent.MaintenanceMetricsException.class,
                () -> agent.reply("request", ContextMode.SUMMARY_RECENT, 1, AgentMemory.Snapshot.empty(null), executor,
                        config(), null, null, List.of(invariant(null))));

        assertEquals(3, failure.summaryMetrics().responseTokens());
        assertTrue(failure.getCause() instanceof InvariantGuard.GuardFailureException);
        assertNotNull(failure.guardMetrics());
    }

    @Test
    void failedGuardDoesNotPersistCandidate() throws Exception {
        AgentModelExecutor executor = mock(AgentModelExecutor.class);
        when(executor.complete(any())).thenReturn(completion("candidate"))
                .thenThrow(new dev.aiadvent.worker.model.ModelExecutionException("guard unavailable"));
        AgentHistoryStore histories = mock(AgentHistoryStore.class);

        assertThrows(InvariantGuard.GuardFailureException.class,
                () -> agent(executor, histories).reply("request", ContextMode.FULL, 4, AgentMemory.Snapshot.empty(null),
                        executor, config(), null, null, List.of(invariant(null))));
        verify(histories, never()).save(any(), any());
    }

    private static ConversationAgent agent(AgentModelExecutor executor, AgentHistoryStore histories) {
        AgentConfig config = config();
        return new ConversationAgent(UUID.randomUUID(), config, new ConversationContext(config.systemPrompt()), executor,
                histories, new ApproximateTokenEstimator(), mock(AgentSummaryStore.class), mock(ConversationSummaryService.class),
                mock(StickyFactsStore.class), mock(StickyFactsService.class), new FullContextPolicy(), new SummaryRecentContextPolicy(),
                new SlidingWindowContextPolicy(), new StickyFactsContextPolicy(), mock(AgentBranchStore.class), null,
                new InvariantGuard(new ObjectMapper(), new ApproximateTokenEstimator()));
    }

    private static AgentConfig config() {
        return new AgentConfig("model", "base", null, 100, null);
    }

    private static Task task() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        return new Task(id, "Goal", new TaskState(TaskStage.PLANNING, "Plan", "Approve", TaskStatus.ACTIVE, 0), "", "", now, now);
    }

    private static Invariant invariant(UUID taskId) {
        return new Invariant(UUID.randomUUID(), taskId == null ? InvariantScope.USER : InvariantScope.TASK, taskId,
                "Persistence", "Persistence must remain PostgreSQL.");
    }

    private static AgentModelExecutor.Completion completion(String content) {
        return new AgentModelExecutor.Completion(content, null);
    }

    private static void assertTrueAt(String source, String... values) {
        int previous = -1;
        for (String value : values) {
            int current = source.indexOf(value);
            if (current <= previous) {
                throw new AssertionError("Expected order for " + value + " in " + source);
            }
            previous = current;
        }
    }
}
