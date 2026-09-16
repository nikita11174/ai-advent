package dev.aiadvent.worker;

import dev.aiadvent.worker.model.AgentModelExecutor;
import dev.aiadvent.worker.model.AgentModelMessage;
import dev.aiadvent.worker.model.AgentModelRequest;

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
        AgentModelExecutor client = mock(AgentModelExecutor.class);
        when(client.complete(any(AgentModelRequest.class)))
                .thenReturn(completion("answer A"), completion("answer B"), completion("follow-up A"),
                        completion("restored answer"));
        var service = service(dialogs, client, histories);

        service.reply(a, "A1");
        service.reply(b, "B1");
        service.reply(UUID.fromString(a.toString().toUpperCase()), "A2");
        service(dialogs, client, histories).reply(a, "after restart");

        ArgumentCaptor<AgentModelRequest> messages = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(client, times(4)).complete(messages.capture());
        var system = new ConversationContext.Message("system", AgentConfig.defaults().systemPrompt());
        assertEquals(List.of(new AgentModelMessage(system.role(), system.content()), new AgentModelMessage("user", "A1")), messages.getAllValues().get(0).messages());
        assertEquals(List.of(new AgentModelMessage(system.role(), system.content()), new AgentModelMessage("user", "B1")), messages.getAllValues().get(1).messages());
        assertEquals(List.of(new AgentModelMessage(system.role(), system.content()), new AgentModelMessage("user", "A1"),
                new AgentModelMessage("assistant", "answer A"), new AgentModelMessage("user", "A2")),
                messages.getAllValues().get(2).messages());
        assertEquals(List.of(new AgentModelMessage(system.role(), system.content()), new AgentModelMessage("user", "A1"),
                new AgentModelMessage("assistant", "answer A"), new AgentModelMessage("user", "A2"),
                new AgentModelMessage("assistant", "follow-up A"),
                new AgentModelMessage("user", "after restart")), messages.getAllValues().get(3).messages());
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
        AgentModelExecutor client = mock(AgentModelExecutor.class);

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
        AgentModelExecutor client = mock(AgentModelExecutor.class);
        when(client.complete(any(AgentModelRequest.class)))
                .thenReturn(completion("a1"), completion("a2"), completion("a3"), completion("compressed"),
                        completion("restored"));
        ConversationSummaryService summaryService = mock(ConversationSummaryService.class);
        when(summaryService.generate(anyList(), isNull(), any(), eq(4)))
                .thenReturn(new SummaryGeneration(new ConversationSummary(4, "remembered summary"),
                        new TokenMetrics(3, 8, 4, null)));
        when(summaryService.generate(anyList(), isNull(), any(), eq(4), any()))
                .thenReturn(new SummaryGeneration(new ConversationSummary(4, "remembered summary"),
                        new TokenMetrics(3, 8, 4, null)));
        var firstService = new AgentDialogService(dialogs, client, firstHistories, new ApproximateTokenEstimator(),
                firstSummaries, summaryService, new StickyFactsStore(directory.resolve("facts"), json),
                mock(StickyFactsService.class), new AgentBranchStore(directory.resolve("branches"), json),
                new AgentMemoryStore(directory.resolve("memory"), json), 0);

        firstService.reply(id, "u1");
        firstService.reply(id, "u2");
        firstService.reply(id, "u3");
        firstService.reply(id, "u4", ContextMode.SUMMARY_RECENT, 2);
        verify(summaryService).generate(anyList(), isNull(), any(), eq(4), any());
        assertEquals(9, firstHistories.load(id).orElseThrow().size());

        ConversationSummaryService restoredSummaryService = mock(ConversationSummaryService.class);
        var restoredHistories = new AgentHistoryStore(directory.resolve("histories"), json);
        var restoredSummaries = new AgentSummaryStore(directory.resolve("summaries"), json);
        var secondService = new AgentDialogService(dialogs, client, restoredHistories, new ApproximateTokenEstimator(),
                restoredSummaries, restoredSummaryService, new StickyFactsStore(directory.resolve("facts"), json),
                mock(StickyFactsService.class), new AgentBranchStore(directory.resolve("branches"), json),
                new AgentMemoryStore(directory.resolve("memory"), json), 0);
        secondService.reply(id, "u5", ContextMode.SUMMARY_RECENT, 4);

        verifyNoInteractions(restoredSummaryService);
        assertEquals(11, restoredHistories.load(id).orElseThrow().size());
        ArgumentCaptor<AgentModelRequest> messages = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(client, times(5)).complete(messages.capture());
        assertEquals(new AgentModelMessage("system", "remembered summary"),
                messages.getValue().messages().get(1));
    }

    @Test
    void branchContinuationsAreIsolatedAndRestoreAfterServiceRestart() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var dialogs = new DialogStore(directory.resolve("dialogs"), json);
        var histories = new AgentHistoryStore(directory.resolve("histories"), json);
        var branchStore = new AgentBranchStore(directory.resolve("branches"), json);
        var factsStore = new StickyFactsStore(directory.resolve("facts"), json);
        UUID id = UUID.fromString(dialogs.create().id());
        var base = List.of(new ConversationContext.Message("system", AgentConfig.defaults().systemPrompt()),
                new ConversationContext.Message("user", "shared decision"),
                new ConversationContext.Message("assistant", "acknowledged"));
        histories.save(id, base);
        AgentModelExecutor client = mock(AgentModelExecutor.class);
        when(client.complete(any(AgentModelRequest.class)))
                .thenReturn(completion("A answer"), completion("B answer"), completion("A follow-up"));
        ConversationSummaryService summaryService = mock(ConversationSummaryService.class);
        StickyFactsService factsService = mock(StickyFactsService.class);
        var service = new AgentDialogService(dialogs, client, histories, new ApproximateTokenEstimator(),
                new AgentSummaryStore(directory.resolve("summaries"), json), summaryService, factsStore, factsService,
                branchStore, new AgentMemoryStore(directory.resolve("memory"), json), 0);

        var checkpoint = service.createCheckpoint(id, null);
        var branchA = service.createBranch(id, checkpoint.id());
        var branchB = service.createBranch(id, checkpoint.id());
        service.reply(id, "PostgreSQL", ContextMode.FULL, 4, branchA.id());
        service.reply(id, "ClickHouse", ContextMode.FULL, 4, branchB.id());

        ArgumentCaptor<AgentModelRequest> messages = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(client, times(2)).complete(messages.capture());
        assertTrue(messages.getAllValues().get(0).messages().stream().noneMatch(message -> message.content().equals("ClickHouse")));
        assertTrue(messages.getAllValues().get(1).messages().stream().noneMatch(message -> message.content().equals("PostgreSQL")));

        var restarted = new AgentDialogService(dialogs, client, new AgentHistoryStore(directory.resolve("histories"), json),
                new ApproximateTokenEstimator(), new AgentSummaryStore(directory.resolve("summaries"), json),
                summaryService, new StickyFactsStore(directory.resolve("facts"), json), factsService, branchStore,
                new AgentMemoryStore(directory.resolve("memory"), json), 0);

        restarted.reply(id, "A follow-up", ContextMode.FULL, 4, branchA.id());
        verify(client, times(3)).complete(messages.capture());
        assertTrue(messages.getValue().messages().stream().anyMatch(message -> message.content().equals("PostgreSQL")));
        assertTrue(messages.getValue().messages().stream().noneMatch(message -> message.content().equals("ClickHouse")));
    }

    @Test
    void differentBranchesCanProcessTurnsConcurrently() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var dialogs = new DialogStore(directory.resolve("dialogs"), json);
        var histories = new AgentHistoryStore(directory.resolve("histories"), json);
        var branchStore = new AgentBranchStore(directory.resolve("branches"), json);
        UUID id = UUID.fromString(dialogs.create().id());
        histories.save(id, List.of(new ConversationContext.Message("system", AgentConfig.defaults().systemPrompt())));
        AgentModelExecutor client = mock(AgentModelExecutor.class);
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        when(client.complete(any(AgentModelRequest.class))).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return completion("answer");
        });
        var service = new AgentDialogService(dialogs, client, histories, new ApproximateTokenEstimator(),
                new AgentSummaryStore(directory.resolve("summaries"), json), mock(ConversationSummaryService.class),
                new StickyFactsStore(directory.resolve("facts"), json), mock(StickyFactsService.class), branchStore,
                new AgentMemoryStore(directory.resolve("memory"), json), 0);
        var checkpoint = service.createCheckpoint(id, null);
        var branchA = service.createBranch(id, checkpoint.id());
        var branchB = service.createBranch(id, checkpoint.id());
        var first = new FutureTask<>(() -> service.reply(id, "A", ContextMode.FULL, 4, branchA.id()));
        var second = new FutureTask<>(() -> service.reply(id, "B", ContextMode.FULL, 4, branchB.id()));
        Thread firstThread = Thread.ofPlatform().start(first);
        Thread secondThread = Thread.ofPlatform().start(second);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            firstThread.join(5000);
            secondThread.join(5000);
        }
        assertEquals("answer", first.get(5, TimeUnit.SECONDS).analysis());
        assertEquals("answer", second.get(5, TimeUnit.SECONDS).analysis());
    }

    @Test
    void concurrentFirstRequestsUseOneAgent() throws Exception {
        DialogStore dialogs = mock(DialogStore.class);
        AgentHistoryStore histories = mock(AgentHistoryStore.class);
        AgentModelExecutor client = mock(AgentModelExecutor.class);
        UUID id = UUID.randomUUID();
        when(histories.load(id)).thenReturn(Optional.empty());
        var start = new CyclicBarrier(2);
        var providerEntered = new CountDownLatch(1);
        var releaseProvider = new CountDownLatch(1);
        when(client.complete(any(AgentModelRequest.class))).thenAnswer(call -> {
            providerEntered.countDown();
            assertTrue(releaseProvider.await(5, TimeUnit.SECONDS));
            return completion("answer");
        });
        var service = service(dialogs, client, histories);
        var rejected = new CountDownLatch(1);
        var first = new FutureTask<>(() -> {
            start.await(5, TimeUnit.SECONDS);
            try { return service.reply(id, "one").analysis(); }
            catch (ConversationAgent.BusyException e) { rejected.countDown(); return "busy"; }
        });
        var second = new FutureTask<>(() -> {
            start.await(5, TimeUnit.SECONDS);
            try { return service.reply(id, "two").analysis(); }
            catch (ConversationAgent.BusyException e) { rejected.countDown(); return "busy"; }
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
        ArgumentCaptor<AgentModelRequest> messages = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(client, times(2)).complete(messages.capture());
        assertEquals(List.of(messages.getAllValues().getFirst().messages().getFirst(), messages.getAllValues().getFirst().messages().getLast(),
                new AgentModelMessage("assistant", "answer"), new AgentModelMessage("user", "follow-up")),
                messages.getValue().messages());
    }

    @Test
    void loadsApplicableMemoryFreshForDialogTaskAndGlobalScopes() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var dialogs = new DialogStore(directory.resolve("dialogs"), json);
        var histories = new AgentHistoryStore(directory.resolve("histories"), json);
        var memoryStore = new AgentMemoryStore(directory.resolve("memory"), json);
        UUID dialogA = UUID.fromString(dialogs.create().id());
        UUID dialogB = UUID.fromString(dialogs.create().id());
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        AgentModelExecutor client = mock(AgentModelExecutor.class);
        when(client.complete(any(AgentModelRequest.class))).thenReturn(completion("answer"));
        var service = new AgentDialogService(dialogs, client, histories, new ApproximateTokenEstimator(),
                mock(AgentSummaryStore.class), mock(ConversationSummaryService.class), mock(StickyFactsStore.class),
                mock(StickyFactsService.class), mock(AgentBranchStore.class), memoryStore, 0);
        service.upsertMemory(dialogA, taskA, AgentMemory.Scope.SHORT_TERM, "codeword", "SATURN");
        service.upsertMemory(dialogA, taskA, AgentMemory.Scope.WORKING, "database", "PostgreSQL");
        service.upsertMemory(dialogA, taskA, AgentMemory.Scope.LONG_TERM, "language", "Java");

        service.reply(dialogA, "A", ContextMode.FULL, 4, null, taskA);
        service.reply(dialogB, "B", ContextMode.FULL, 4, null, taskA);
        service.reply(dialogB, "B2", ContextMode.FULL, 4, null, taskB);

        ArgumentCaptor<AgentModelRequest> outbound = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(client, times(3)).complete(outbound.capture());
        String first = outbound.getAllValues().get(0).messages().get(outbound.getAllValues().get(0).messages().size() - 2).content();
        String sharedTask = outbound.getAllValues().get(1).messages().get(outbound.getAllValues().get(1).messages().size() - 2).content();
        String otherTask = outbound.getAllValues().get(2).messages().get(outbound.getAllValues().get(2).messages().size() - 2).content();
        assertTrue(first.contains("SATURN") && first.contains("PostgreSQL") && first.contains("Java"));
        assertFalse(sharedTask.contains("SATURN"));
        assertTrue(sharedTask.contains("PostgreSQL") && sharedTask.contains("Java"));
        assertFalse(otherTask.contains("SATURN") || otherTask.contains("PostgreSQL"));
        assertTrue(otherTask.contains("Java"));

        var restarted = new AgentMemoryStore(directory.resolve("memory"), json).load(dialogA, taskA);
        assertEquals("SATURN", restarted.shortTerm().get("codeword"));
        assertEquals("PostgreSQL", restarted.working().get("database"));
        assertEquals("Java", restarted.longTerm().get("language"));
    }

    @Test
    void branchUsesFullContextButPersistsNoMemoryProjectionInTopology() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var dialogs = new DialogStore(directory.resolve("dialogs"), json);
        var histories = new AgentHistoryStore(directory.resolve("histories"), json);
        var branchStore = new AgentBranchStore(directory.resolve("branches"), json);
        var memoryStore = new AgentMemoryStore(directory.resolve("memory"), json);
        UUID dialogId = UUID.fromString(dialogs.create().id());
        AgentModelExecutor client = mock(AgentModelExecutor.class);
        when(client.complete(any(AgentModelRequest.class))).thenReturn(completion("answer"));
        var service = new AgentDialogService(dialogs, client, histories, new ApproximateTokenEstimator(),
                mock(AgentSummaryStore.class), mock(ConversationSummaryService.class), mock(StickyFactsStore.class),
                mock(StickyFactsService.class), branchStore, memoryStore, 0);
        service.upsertMemory(dialogId, null, AgentMemory.Scope.SHORT_TERM, "key", "MEMORY_ONLY_VALUE");
        AgentBranchStore.Checkpoint checkpoint = service.createCheckpoint(dialogId, null);
        AgentBranchStore.Branch branch = service.createBranch(dialogId, checkpoint.id());

        assertThrows(IllegalArgumentException.class,
                () -> service.reply(dialogId, "invalid", ContextMode.SLIDING_WINDOW, 4, branch.id(), null));
        service.reply(dialogId, "command", ContextMode.FULL, 4, branch.id(), null);

        List<ConversationContext.Message> branchHistory = branchStore.loadBranch(dialogId, branch.id());
        assertTrue(branchHistory.stream().noneMatch(message -> message.content().contains("MEMORY_ONLY_VALUE")));
        ArgumentCaptor<AgentModelRequest> outbound = ArgumentCaptor.forClass(AgentModelRequest.class);
        verify(client).complete(outbound.capture());
        assertTrue(outbound.getValue().messages().stream().anyMatch(message -> message.content().contains("MEMORY_ONLY_VALUE")));
    }

    private AgentDialogService service(DialogStore dialogs, AgentModelExecutor client, AgentHistoryStore histories) {
        return new AgentDialogService(dialogs, client, histories, new ApproximateTokenEstimator(),
                mock(AgentSummaryStore.class), mock(ConversationSummaryService.class), mock(StickyFactsStore.class),
                mock(StickyFactsService.class), mock(AgentBranchStore.class),
                new AgentMemoryStore(directory.resolve("memory"), new ObjectMapper().findAndRegisterModules()), 0);
    }

    private AgentModelExecutor.Completion completion(String content) {
        return new AgentModelExecutor.Completion(content, null);
    }
}
