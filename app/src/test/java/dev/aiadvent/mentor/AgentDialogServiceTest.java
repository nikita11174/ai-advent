package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentDialogServiceTest {
    @TempDir
    Path directory;

    @Test
    void restoresRawHistoryAcrossServicesAndKeepsDialogsAndUiArchivesIsolated() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var dialogs = new DialogStore(directory.resolve("dialogs"), json);
        var histories = new AgentHistoryStore(directory.resolve("histories"), json);
        UUID a = UUID.fromString(dialogs.create().id());
        UUID b = UUID.fromString(dialogs.create().id());
        dialogs.update(a.toString(), new DialogStore.DialogUpdate("archive", json.readTree(
                "{\"exchanges\":[{\"mode\":\"AGENT\",\"input\":\"old fact\",\"free\":{\"analysis\":\"old answer\"}}]}")));
        byte[] originalArchive = Files.readAllBytes(directory.resolve("dialogs").resolve(a + ".json"));
        DeepSeekClient client = mock(DeepSeekClient.class);
        when(client.complete(anyList(), anyString(), isNull(), isNull()))
                .thenReturn(completion("answer A"), completion("answer B"), completion("follow-up A"),
                        completion("restored answer"));
        var service = service(dialogs, client, histories);

        service.reply(a, "A1");
        service.reply(b, "B1");
        service.reply(UUID.fromString(a.toString().toUpperCase()), "A2");
        service(dialogs, client, histories).reply(a, "after restart");

        ArgumentCaptor<List<ConversationContext.Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(client, times(4)).complete(messages.capture(), anyString(), isNull(), isNull());
        var system = new ConversationContext.Message("system", AgentConfig.defaults().systemPrompt());
        assertEquals(List.of(system, new ConversationContext.Message("user", "A1")), messages.getAllValues().get(0));
        assertEquals(List.of(system, new ConversationContext.Message("user", "B1")), messages.getAllValues().get(1));
        assertEquals(List.of(system, new ConversationContext.Message("user", "A1"),
                new ConversationContext.Message("assistant", "answer A"), new ConversationContext.Message("user", "A2")),
                messages.getAllValues().get(2));
        assertEquals(List.of(system, new ConversationContext.Message("user", "A1"),
                new ConversationContext.Message("assistant", "answer A"), new ConversationContext.Message("user", "A2"),
                new ConversationContext.Message("assistant", "follow-up A"),
                new ConversationContext.Message("user", "after restart")), messages.getAllValues().get(3));
        assertArrayEquals(originalArchive, Files.readAllBytes(directory.resolve("dialogs").resolve(a + ".json")));
        assertEquals(2, dialogs.list().size());
    }

    @Test
    void malformedHistoryDoesNotCallProvider() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var dialogs = new DialogStore(directory.resolve("dialogs"), json);
        var histories = new AgentHistoryStore(directory.resolve("histories"), json);
        UUID id = UUID.fromString(dialogs.create().id());
        Path history = directory.resolve("histories").resolve(id + ".json");
        Files.createDirectories(history.getParent());
        Files.writeString(history, "{\"messages\":[{\"role\":\"user\",\"content\":\"invalid\"}]}");
        DeepSeekClient client = mock(DeepSeekClient.class);

        assertThrows(IOException.class, () -> service(dialogs, client, histories).reply(id, "next"));
        verifyNoInteractions(client);
    }

    @Test
    void freshServiceRestoresSummaryStateWithoutRegeneratingIt() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var dialogs = new DialogStore(directory.resolve("dialogs"), json);
        var firstHistories = new AgentHistoryStore(directory.resolve("histories"), json);
        var firstSummaries = new AgentSummaryStore(directory.resolve("summaries"), json);
        UUID id = UUID.fromString(dialogs.create().id());
        DeepSeekClient client = mock(DeepSeekClient.class);
        when(client.complete(anyList(), anyString(), isNull(), isNull()))
                .thenReturn(completion("a1"), completion("a2"), completion("a3"), completion("compressed"),
                        completion("restored"));
        ConversationSummaryService summaryService = mock(ConversationSummaryService.class);
        when(summaryService.generate(anyList(), isNull(), any(), eq(4)))
                .thenReturn(new SummaryGeneration(new ConversationSummary(4, "remembered summary"),
                        new TokenMetrics(3, 8, 4, null)));
        var firstService = new AgentDialogService(dialogs, client, firstHistories, new ApproximateTokenEstimator(),
                firstSummaries, summaryService, 0);

        firstService.reply(id, "u1");
        firstService.reply(id, "u2");
        firstService.reply(id, "u3");
        firstService.reply(id, "u4", ContextMode.SUMMARY_RECENT, 2);
        verify(summaryService).generate(anyList(), isNull(), any(), eq(4));
        assertEquals(9, firstHistories.load(id).orElseThrow().size());

        ConversationSummaryService restoredSummaryService = mock(ConversationSummaryService.class);
        var restoredHistories = new AgentHistoryStore(directory.resolve("histories"), json);
        var restoredSummaries = new AgentSummaryStore(directory.resolve("summaries"), json);
        var secondService = new AgentDialogService(dialogs, client, restoredHistories, new ApproximateTokenEstimator(),
                restoredSummaries, restoredSummaryService, 0);
        secondService.reply(id, "u5", ContextMode.SUMMARY_RECENT, 4);

        verifyNoInteractions(restoredSummaryService);
        assertEquals(11, restoredHistories.load(id).orElseThrow().size());
        ArgumentCaptor<List<ConversationContext.Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(client, times(5)).complete(messages.capture(), anyString(), isNull(), isNull());
        assertEquals(new ConversationContext.Message("system", "remembered summary"),
                messages.getValue().get(1));
    }

    @Test
    void concurrentFirstRequestsUseOneAgent() throws Exception {
        DialogStore dialogs = mock(DialogStore.class);
        AgentHistoryStore histories = mock(AgentHistoryStore.class);
        DeepSeekClient client = mock(DeepSeekClient.class);
        UUID id = UUID.randomUUID();
        when(histories.load(id)).thenReturn(Optional.empty());
        var start = new CyclicBarrier(2);
        var providerEntered = new CountDownLatch(1);
        var releaseProvider = new CountDownLatch(1);
        when(client.complete(anyList(), anyString(), isNull(), isNull())).thenAnswer(call -> {
            providerEntered.countDown();
            assertTrue(releaseProvider.await(5, TimeUnit.SECONDS));
            return completion("answer");
        });
        var service = service(dialogs, client, histories);
        var rejected = new CountDownLatch(1);
        var first = new FutureTask<>(() -> {
            start.await(5, TimeUnit.SECONDS);
            try { return service.reply(id, "one").analysis(); }
            catch (EngineeringReviewAgent.BusyException e) { rejected.countDown(); return "busy"; }
        });
        var second = new FutureTask<>(() -> {
            start.await(5, TimeUnit.SECONDS);
            try { return service.reply(id, "two").analysis(); }
            catch (EngineeringReviewAgent.BusyException e) { rejected.countDown(); return "busy"; }
        });
        Thread firstThread = Thread.ofPlatform().start(first);
        Thread secondThread = Thread.ofPlatform().start(second);
        try {
            assertTrue(providerEntered.await(5, TimeUnit.SECONDS));
            assertTrue(rejected.await(5, TimeUnit.SECONDS));
        } finally {
            releaseProvider.countDown();
            firstThread.join(5000);
            secondThread.join(5000);
        }
        assertTrue(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)).containsAll(List.of("busy", "answer")));
        service.reply(id, "follow-up");
        ArgumentCaptor<List<ConversationContext.Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(client, times(2)).complete(messages.capture(), anyString(), isNull(), isNull());
        assertEquals(List.of(messages.getAllValues().getFirst().getFirst(), messages.getAllValues().getFirst().getLast(),
                new ConversationContext.Message("assistant", "answer"), new ConversationContext.Message("user", "follow-up")),
                messages.getValue());
    }

    private AgentDialogService service(DialogStore dialogs, DeepSeekClient client, AgentHistoryStore histories) {
        return new AgentDialogService(dialogs, client, histories, new ApproximateTokenEstimator(),
                mock(AgentSummaryStore.class), mock(ConversationSummaryService.class), 0);
    }

    private DeepSeekClient.Completion completion(String content) {
        return new DeepSeekClient.Completion(content, "stop", null);
    }
}
