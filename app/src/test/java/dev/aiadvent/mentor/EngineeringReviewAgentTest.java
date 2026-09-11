package dev.aiadvent.mentor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EngineeringReviewAgentTest {
    private final DeepSeekClient client = mock(DeepSeekClient.class);
    private final AgentHistoryStore histories = mock(AgentHistoryStore.class);

    @Test
    void sendsTheWholeConversationAndPersistsCompletedSnapshots() throws Exception {
        AgentConfig config = AgentConfig.defaults();
        var agent = agent(UUID.randomUUID(), config);
        when(client.complete(anyList(), eq(config.model()), isNull(), isNull()))
                .thenReturn(completion("answer1"), completion("answer2"), completion("answer3"));

        assertEquals("answer1", agent.reply(" user1\n").analysis());
        assertEquals("answer2", agent.reply("user2").analysis());
        assertEquals("answer3", agent.reply("user3").analysis());

        ArgumentCaptor<List<ConversationContext.Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(client, times(3)).complete(messages.capture(), eq(config.model()), isNull(), isNull());
        var system = new ConversationContext.Message("system", config.systemPrompt());
        assertEquals(List.of(system, new ConversationContext.Message("user", " user1\n")), messages.getAllValues().get(0));
        assertEquals(List.of(system, new ConversationContext.Message("user", " user1\n"),
                new ConversationContext.Message("assistant", "answer1"), new ConversationContext.Message("user", "user2")),
                messages.getAllValues().get(1));
        assertEquals(List.of(system, new ConversationContext.Message("user", " user1\n"),
                new ConversationContext.Message("assistant", "answer1"), new ConversationContext.Message("user", "user2"),
                new ConversationContext.Message("assistant", "answer2"), new ConversationContext.Message("user", "user3")),
                messages.getAllValues().get(2));
        assertThrows(UnsupportedOperationException.class, () -> messages.getValue().clear());
        verify(histories, times(3)).save(any(), anyList());
    }

    @Test
    void providerFailureLeavesRuntimeAndPersistedHistoryUnchanged() throws Exception {
        AgentConfig config = AgentConfig.defaults();
        var agent = agent(UUID.randomUUID(), config);
        when(client.complete(anyList(), anyString(), isNull(), isNull()))
                .thenReturn(completion("answer1")).thenThrow(new DeepSeekException("Unavailable"))
                .thenReturn(completion("answer2"));

        agent.reply("user1");
        assertThrows(DeepSeekException.class, () -> agent.reply("failed input"));
        assertEquals("answer2", agent.reply("user2").analysis());

        ArgumentCaptor<List<ConversationContext.Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(client, times(3)).complete(messages.capture(), anyString(), isNull(), isNull());
        assertEquals(List.of(new ConversationContext.Message("system", config.systemPrompt()),
                new ConversationContext.Message("user", "user1"), new ConversationContext.Message("assistant", "answer1"),
                new ConversationContext.Message("user", "user2")), messages.getValue());
        verify(histories, times(2)).save(any(), anyList());
    }

    @Test
    void saveFailureDoesNotAdvanceRuntimeHistory() throws Exception {
        AgentConfig config = AgentConfig.defaults();
        UUID id = UUID.randomUUID();
        var agent = agent(id, config);
        when(client.complete(anyList(), anyString(), isNull(), isNull()))
                .thenReturn(completion("answer1"), completion("lost answer"), completion("answer2"));
        doNothing().doThrow(new IOException("storage unavailable")).doNothing().when(histories).save(eq(id), anyList());

        agent.reply("user1");
        assertThrows(IOException.class, () -> agent.reply("not saved"));
        assertEquals("answer2", agent.reply("user2").analysis());

        ArgumentCaptor<List<ConversationContext.Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(client, times(3)).complete(messages.capture(), anyString(), isNull(), isNull());
        assertEquals(List.of(new ConversationContext.Message("system", config.systemPrompt()),
                new ConversationContext.Message("user", "user1"), new ConversationContext.Message("assistant", "answer1"),
                new ConversationContext.Message("user", "user2")), messages.getValue());
    }

    @Test
    void returnsLocalEstimatesAndIndependentProviderUsageForTheActualOutboundStack() throws Exception {
        AgentConfig config = AgentConfig.defaults();
        var agent = agent(UUID.randomUUID(), config);
        ProviderUsage usage = new ProviderUsage(101L, 23L, 124L);
        when(client.complete(anyList(), anyString(), isNull(), isNull()))
                .thenReturn(new DeepSeekClient.Completion("answer", "stop", usage));

        AgentReply reply = agent.reply("request");

        var estimator = new ApproximateTokenEstimator();
        List<ConversationContext.Message> outbound = List.of(new ConversationContext.Message("system", config.systemPrompt()),
                new ConversationContext.Message("user", "request"));
        assertEquals(new TokenMetrics(estimator.estimateText("request"), estimator.estimateMessages(outbound),
                estimator.estimateText("answer"), usage), reply.metrics());
    }

    @Test
    void contextOverflowDoesNotCallProviderOrPersistTheRejectedInput() throws Exception {
        var estimator = new ApproximateTokenEstimator();
        AgentConfig base = AgentConfig.defaults();
        List<ConversationContext.Message> acceptedNext = List.of(
                new ConversationContext.Message("system", base.systemPrompt()),
                new ConversationContext.Message("user", "first"),
                new ConversationContext.Message("assistant", "answer"),
                new ConversationContext.Message("user", "next"));
        AgentConfig config = new AgentConfig(base.model(), base.systemPrompt(), base.temperature(), base.maxTokens(),
                Math.toIntExact(estimator.estimateMessages(acceptedNext)));
        var agent = agent(UUID.randomUUID(), config);
        when(client.complete(anyList(), anyString(), isNull(), isNull()))
                .thenReturn(completion("answer"), completion("next answer"));

        agent.reply("first");
        assertThrows(EngineeringReviewAgent.ContextLimitExceededException.class,
                () -> agent.reply("this input is deliberately too long for the configured limit"));
        assertEquals("next answer", agent.reply("next").analysis());

        ArgumentCaptor<List<ConversationContext.Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(client, times(2)).complete(messages.capture(), anyString(), isNull(), isNull());
        assertEquals(acceptedNext, messages.getValue());
        verify(histories, times(2)).save(any(), anyList());
    }

    @Test
    void agentsUseTheirOwnConfiguration() throws Exception {
        var first = agent(UUID.randomUUID(), new AgentConfig("model-a", "instruction A", 0.0, 450, null));
        var second = agent(UUID.randomUUID(), new AgentConfig("model-b", "instruction B", 1.2, 900, null));
        when(client.complete(anyList(), anyString(), anyDouble(), anyInt())).thenReturn(completion("answer"));

        first.reply("A");
        second.reply("B");

        verify(client).complete(List.of(new ConversationContext.Message("system", "instruction A"),
                new ConversationContext.Message("user", "A")), "model-a", 0.0, 450);
        verify(client).complete(List.of(new ConversationContext.Message("system", "instruction B"),
                new ConversationContext.Message("user", "B")), "model-b", 1.2, 900);
    }

    @Test
    void rejectsAnOverlappingTurnButAllowsAnotherAgentAndReleasesTheLock() throws Exception {
        var first = agent(UUID.randomUUID(), AgentConfig.defaults());
        var second = agent(UUID.randomUUID(), AgentConfig.defaults());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.complete(anyList(), anyString(), isNull(), isNull())).thenAnswer(call -> {
            List<ConversationContext.Message> messages = call.getArgument(0);
            if (messages.getLast().content().equals("A1")) {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return completion("answer");
        });
        var inFlight = new FutureTask<>(() -> first.reply("A1"));
        Thread thread = Thread.ofPlatform().start(inFlight);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(EngineeringReviewAgent.BusyException.class, () -> first.reply("overlap"));
            assertEquals("answer", second.reply("B1").analysis());
        } finally {
            release.countDown();
            thread.join(5000);
        }
        assertEquals("answer", inFlight.get(5, TimeUnit.SECONDS).analysis());
        first.reply("A2");
        verify(client, times(3)).complete(anyList(), anyString(), isNull(), isNull());
        verify(client).complete(List.of(new ConversationContext.Message("system", AgentConfig.defaults().systemPrompt()),
                new ConversationContext.Message("user", "A1"), new ConversationContext.Message("assistant", "answer"),
                new ConversationContext.Message("user", "A2")), AgentConfig.defaults().model(), null, null);
    }

    @Test
    void rejectsInvalidInputAndConfigurationWithoutCallingProvider() {
        var agent = agent(UUID.randomUUID(), AgentConfig.defaults());
        assertThrows(IllegalArgumentException.class, () -> agent.reply(null));
        assertThrows(IllegalArgumentException.class, () -> agent.reply(" \n"));
        assertThrows(IllegalArgumentException.class, () -> new AgentConfig("", "system", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentConfig("model", " ", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentConfig("model", "system", Double.NaN, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentConfig("model", "system", null, 99, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentConfig("model", "system", null, 2001, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentConfig("model", "system", null, null, 0));
        verifyNoInteractions(client, histories);
    }

    private EngineeringReviewAgent agent(UUID id, AgentConfig config) {
        return new EngineeringReviewAgent(id, config, new ConversationContext(config.systemPrompt()), client, histories,
                new ApproximateTokenEstimator());
    }

    private DeepSeekClient.Completion completion(String content) {
        return new DeepSeekClient.Completion(content, "stop", null);
    }
}
