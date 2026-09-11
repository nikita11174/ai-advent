package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    void isolatesDialogsAndStartsFreshWithoutRestoringOrWritingTheUiArchive() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var store = new DialogStore(directory, json);
        UUID a = UUID.fromString(store.create().id());
        UUID b = UUID.fromString(store.create().id());
        store.update(a.toString(), new DialogStore.DialogUpdate("archive", json.readTree(
                "{\"exchanges\":[{\"mode\":\"AGENT\",\"input\":\"old fact\",\"free\":{\"analysis\":\"old answer\"}}]}")));
        byte[] original = Files.readAllBytes(directory.resolve(a + ".json"));
        DeepSeekClient client = mock(DeepSeekClient.class);
        when(client.complete(anyList(), anyString(), isNull(), isNull()))
                .thenReturn("answer A", "answer B", "follow-up A", "fresh answer");
        var service = new AgentDialogService(store, client);

        service.reply(a, "A1");
        service.reply(b, "B1");
        service.reply(UUID.fromString(a.toString().toUpperCase()), "A2");
        new AgentDialogService(store, client).reply(a, "after restart");

        ArgumentCaptor<List<ConversationContext.Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(client, times(4)).complete(messages.capture(), anyString(), isNull(), isNull());
        var system = new ConversationContext.Message("system", AgentConfig.defaults().systemPrompt());
        assertEquals(List.of(system, new ConversationContext.Message("user", "A1")), messages.getAllValues().get(0));
        assertEquals(List.of(system, new ConversationContext.Message("user", "B1")), messages.getAllValues().get(1));
        assertEquals(List.of(system, new ConversationContext.Message("user", "A1"),
                new ConversationContext.Message("assistant", "answer A"),
                new ConversationContext.Message("user", "A2")), messages.getAllValues().get(2));
        assertEquals(List.of(system, new ConversationContext.Message("user", "after restart")), messages.getAllValues().get(3));
        assertArrayEquals(original, Files.readAllBytes(directory.resolve(a + ".json")));
        assertEquals(2, store.list().size());
    }

    @Test
    void concurrentFirstRequestsUseOneAgent() throws Exception {
        DialogStore store = mock(DialogStore.class);
        DeepSeekClient client = mock(DeepSeekClient.class);
        UUID id = UUID.randomUUID();
        var start = new CyclicBarrier(2);
        var providerEntered = new CountDownLatch(1);
        var releaseProvider = new CountDownLatch(1);
        when(client.complete(anyList(), anyString(), isNull(), isNull())).thenAnswer(call -> {
            providerEntered.countDown();
            assertTrue(releaseProvider.await(5, TimeUnit.SECONDS));
            return "answer";
        });
        var service = new AgentDialogService(store, client);
        var rejected = new CountDownLatch(1);
        var first = new FutureTask<>(() -> {
            start.await(5, TimeUnit.SECONDS);
            try { return service.reply(id, "one"); }
            catch (EngineeringReviewAgent.BusyException e) { rejected.countDown(); return "busy"; }
        });
        var second = new FutureTask<>(() -> {
            start.await(5, TimeUnit.SECONDS);
            try { return service.reply(id, "two"); }
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
        assertEquals(List.of(messages.getAllValues().getFirst().getFirst(),
                messages.getAllValues().getFirst().getLast(),
                new ConversationContext.Message("assistant", "answer"),
                new ConversationContext.Message("user", "follow-up")), messages.getValue());
    }
}
