package dev.aiadvent.worker.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.context.ApproximateTokenEstimator;
import dev.aiadvent.worker.context.ContextLimitExceededException;
import dev.aiadvent.worker.context.ContextMode;
import dev.aiadvent.worker.context.FullContextPolicy;
import dev.aiadvent.worker.context.SlidingWindowContextPolicy;
import dev.aiadvent.worker.context.StickyFactsContextPolicy;
import dev.aiadvent.worker.context.SummaryRecentContextPolicy;
import dev.aiadvent.worker.dialog.AgentBranchStore;
import dev.aiadvent.worker.dialog.AgentHistoryStore;
import dev.aiadvent.worker.dialog.ConversationContext;
import dev.aiadvent.worker.memory.AgentMemory;
import dev.aiadvent.worker.memory.AgentSummaryStore;
import dev.aiadvent.worker.memory.StickyFacts;
import dev.aiadvent.worker.memory.StickyFactsStore;
import dev.aiadvent.worker.model.AgentModelExecutor;
import dev.aiadvent.worker.model.AgentModelMessage;
import dev.aiadvent.worker.model.AgentModelRequest;
import dev.aiadvent.worker.profile.Profile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileContextIntegrationTest {
    @Test
    void noProfileKeepsTheExistingMainRequest() throws Exception {
        AgentConfig config = new AgentConfig("model", "base system", null, null, null);
        AgentModelExecutor executor = mock(AgentModelExecutor.class);
        AgentHistoryStore histories = mock(AgentHistoryStore.class);
        when(executor.complete(any())).thenReturn(completion("answer"));

        agent(UUID.randomUUID(), config, new ConversationContext(config.systemPrompt()), executor, histories,
                new ApproximateTokenEstimator(), mock(AgentSummaryStore.class), mock(ConversationSummaryService.class),
                mock(StickyFactsStore.class), mock(StickyFactsService.class)).reply("command", ContextMode.FULL, 4,
                AgentMemory.Snapshot.empty(null), executor, config, null);

        ArgumentCaptor<AgentModelRequest> request = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(executor).complete(request.capture());
        assertEquals(List.of(new AgentModelMessage("system", "base system"), new AgentModelMessage("user", "command")),
                request.getValue().messages());
    }

    @Test
    void projectsProfileOnceAcrossEveryContextModeWithoutPersistingIt() throws Exception {
        Profile profile = new Profile(UUID.randomUUID(), "Reviewer", "Focus on evidence", "Concise", "Markdown");
        AgentConfig config = new AgentConfig("model", "base system", null, null, null);
        AgentModelExecutor executor = mock(AgentModelExecutor.class);
        AgentHistoryStore histories = mock(AgentHistoryStore.class);
        StickyFactsStore factsStore = mock(StickyFactsStore.class);
        StickyFactsService factsService = mock(StickyFactsService.class);
        when(executor.complete(any())).thenReturn(completion("answer"));
        when(factsStore.load(any())).thenReturn(java.util.Optional.empty());
        when(factsService.update(any(), eq("command"), eq(config), eq(1), same(executor)))
                .thenReturn(new StickyFactsGeneration(StickyFacts.empty(), new TokenMetrics(0, 0, 0, null)));

        for (ContextMode mode : ContextMode.values()) {
            agent(UUID.randomUUID(), config, new ConversationContext(config.systemPrompt()), executor, histories,
                    new ApproximateTokenEstimator(), mock(AgentSummaryStore.class), mock(ConversationSummaryService.class),
                    factsStore, factsService).reply("command", mode, 2, AgentMemory.Snapshot.empty(null), executor, config,
                    profile);
        }

        ArgumentCaptor<AgentModelRequest> requests = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(executor, times(4)).complete(requests.capture());
        for (AgentModelRequest request : requests.getAllValues()) {
            assertEquals(new AgentModelMessage("system", "base system"), request.messages().getFirst());
            assertEquals(1, request.messages().stream().filter(message -> message.content().contains("Profile: Reviewer")).count());
            assertTrue(request.messages().get(1).content().contains("does not conflict with the application system instructions"));
            assertTrue(request.messages().get(1).content().contains("Instructions: Focus on evidence"));
            assertTrue(request.messages().get(1).content().contains("Response style: Concise"));
            assertTrue(request.messages().get(1).content().contains("Response format: Markdown"));
            assertEquals("command", request.messages().getLast().content());
        }
        ArgumentCaptor<List<ConversationContext.Message>> persisted = ArgumentCaptor.forClass(List.class);
        verify(histories, times(4)).save(any(), persisted.capture());
        for (List<ConversationContext.Message> history : persisted.getAllValues()) {
            assertFalse(containsProfileHistory(history));
        }
    }

    @Test
    void profileReachesOnlyTheMainCallAndNotMaintenanceCalls() throws Exception {
        Profile profile = new Profile(UUID.randomUUID(), "Reviewer", "PROFILE_MARKER", "Concise", "Markdown");
        AgentConfig config = new AgentConfig("model", "base system", null, null, null);
        AgentModelExecutor executor = mock(AgentModelExecutor.class);
        var summaries = mock(AgentSummaryStore.class);
        UUID summaryId = UUID.randomUUID();
        when(summaries.load(summaryId)).thenReturn(java.util.Optional.empty());
        when(executor.complete(any())).thenReturn(completion("summary"), completion("answer"));
        ConversationAgent summaryAgent = agent(summaryId, config, history(config), executor, mock(AgentHistoryStore.class),
                new ApproximateTokenEstimator(), summaries, new ConversationSummaryService(executor, new ApproximateTokenEstimator()),
                mock(StickyFactsStore.class), mock(StickyFactsService.class));

        summaryAgent.reply("next", ContextMode.SUMMARY_RECENT, 1, AgentMemory.Snapshot.empty(null), executor, config, profile);

        ArgumentCaptor<AgentModelRequest> summaryRequests = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(executor, times(2)).complete(summaryRequests.capture());
        assertFalse(containsProfile(summaryRequests.getAllValues().get(0).messages()));
        assertTrue(containsProfile(summaryRequests.getAllValues().get(1).messages()));

        AgentModelExecutor stickyExecutor = mock(AgentModelExecutor.class);
        when(stickyExecutor.complete(any())).thenReturn(completion("{\"facts\":{}}"), completion("answer"));
        ConversationAgent stickyAgent = agent(UUID.randomUUID(), config, new ConversationContext(config.systemPrompt()), stickyExecutor,
                mock(AgentHistoryStore.class), new ApproximateTokenEstimator(), mock(AgentSummaryStore.class),
                mock(ConversationSummaryService.class), mock(StickyFactsStore.class),
                new StickyFactsService(stickyExecutor, new ApproximateTokenEstimator(), new ObjectMapper()));

        stickyAgent.reply("next", ContextMode.STICKY_FACTS, 1, AgentMemory.Snapshot.empty(null), stickyExecutor, config, profile);

        ArgumentCaptor<AgentModelRequest> stickyRequests = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(stickyExecutor, times(2)).complete(stickyRequests.capture());
        assertFalse(containsProfile(stickyRequests.getAllValues().get(0).messages()));
        assertTrue(containsProfile(stickyRequests.getAllValues().get(1).messages()));
    }

    @Test
    void profileCountsTowardTheExistingContextLimit() throws Exception {
        AgentConfig base = new AgentConfig("model", "base system", null, null, null);
        var estimator = new ApproximateTokenEstimator();
        long limit = estimator.estimateMessages(List.of(new ConversationContext.Message("system", base.systemPrompt()),
                new ConversationContext.Message("user", "command")));
        AgentConfig limited = new AgentConfig(base.model(), base.systemPrompt(), null, null, Math.toIntExact(limit));
        AgentModelExecutor executor = mock(AgentModelExecutor.class);
        AgentHistoryStore histories = mock(AgentHistoryStore.class);
        Profile profile = new Profile(UUID.randomUUID(), "Reviewer", "long enough", "Concise", "Markdown");

        assertThrows(ContextLimitExceededException.class, () -> agent(UUID.randomUUID(), limited,
                new ConversationContext(limited.systemPrompt()), executor, histories, estimator, mock(AgentSummaryStore.class),
                mock(ConversationSummaryService.class), mock(StickyFactsStore.class), mock(StickyFactsService.class))
                .reply("command", ContextMode.FULL, 4, AgentMemory.Snapshot.empty(null), executor, limited, profile));

        verifyNoInteractions(executor);
        verify(histories, never()).save(any(), anyList());
    }

    @Test
    void changingProfileChangesOnlyTheNextEffectiveCall() throws Exception {
        AgentConfig config = new AgentConfig("model", "base system", null, null, null);
        AgentModelExecutor executor = mock(AgentModelExecutor.class);
        AgentHistoryStore histories = mock(AgentHistoryStore.class);
        when(executor.complete(any())).thenReturn(completion("first"), completion("second"));
        ConversationAgent agent = agent(UUID.randomUUID(), config, new ConversationContext(config.systemPrompt()), executor, histories,
                new ApproximateTokenEstimator(), mock(AgentSummaryStore.class), mock(ConversationSummaryService.class),
                mock(StickyFactsStore.class), mock(StickyFactsService.class));

        agent.reply("first", ContextMode.FULL, 4, AgentMemory.Snapshot.empty(null), executor, config,
                new Profile(UUID.randomUUID(), "First", "FIRST_PROFILE", "Brief", "Markdown"));
        agent.reply("second", ContextMode.FULL, 4, AgentMemory.Snapshot.empty(null), executor, config,
                new Profile(UUID.randomUUID(), "Second", "SECOND_PROFILE", "Detailed", "Bullets"));

        ArgumentCaptor<AgentModelRequest> requests = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(executor, times(2)).complete(requests.capture());
        assertTrue(contains(requests.getAllValues().get(0).messages(), "FIRST_PROFILE"));
        assertFalse(contains(requests.getAllValues().get(0).messages(), "SECOND_PROFILE"));
        assertTrue(contains(requests.getAllValues().get(1).messages(), "SECOND_PROFILE"));
        assertFalse(contains(requests.getAllValues().get(1).messages(), "FIRST_PROFILE"));
        ArgumentCaptor<List<ConversationContext.Message>> persisted = ArgumentCaptor.forClass(List.class);
        verify(histories, times(2)).save(any(), persisted.capture());
        assertFalse(persisted.getAllValues().stream().anyMatch(ProfileContextIntegrationTest::containsProfileHistory));
    }

    private static ConversationAgent agent(UUID id, AgentConfig config, ConversationContext context,
                                           AgentModelExecutor executor, AgentHistoryStore histories,
                                           ApproximateTokenEstimator estimator, AgentSummaryStore summaries,
                                           ConversationSummaryService summaryService, StickyFactsStore factsStore,
                                           StickyFactsService factsService) {
        return new ConversationAgent(id, config, context, executor, histories, estimator, summaries, summaryService,
                factsStore, factsService, new FullContextPolicy(), new SummaryRecentContextPolicy(),
                new SlidingWindowContextPolicy(), new StickyFactsContextPolicy(), mock(AgentBranchStore.class), null);
    }

    private static ConversationContext history(AgentConfig config) {
        return new ConversationContext(List.of(new ConversationContext.Message("system", config.systemPrompt()),
                new ConversationContext.Message("user", "old"), new ConversationContext.Message("assistant", "answer")));
    }

    private static AgentModelExecutor.Completion completion(String content) {
        return new AgentModelExecutor.Completion(content, null);
    }

    private static boolean containsProfile(List<? extends AgentModelMessage> messages) {
        return contains(messages, "PROFILE_MARKER") || contains(messages, "Profile: Reviewer")
                || contains(messages, "FIRST_PROFILE") || contains(messages, "SECOND_PROFILE");
    }

    private static boolean containsProfileHistory(List<ConversationContext.Message> messages) {
        return messages.stream().anyMatch(message -> message.content().contains("PROFILE_MARKER")
                || message.content().contains("Profile: Reviewer") || message.content().contains("FIRST_PROFILE")
                || message.content().contains("SECOND_PROFILE"));
    }

    private static boolean contains(List<? extends AgentModelMessage> messages, String value) {
        return messages.stream().anyMatch(message -> message.content().contains(value));
    }
}
