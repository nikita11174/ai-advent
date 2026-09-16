package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentModelCatalogTest {
    @TempDir Path directory;

    @Test
    void selectionsReachTheSameAgentHistoryAndScopedMemory() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var dialogs = new DialogStore(directory.resolve("dialogs"), json);
        var histories = new AgentHistoryStore(directory.resolve("history"), json);
        var deepSeek = mock(DeepSeekClient.class);
        var openAi = mock(OpenAiResponsesClient.class);
        var models = new AgentModelCatalog(new DeepSeekAgentModelExecutor(deepSeek), new OpenAiAgentModelExecutor(openAi));
        var estimator = new ApproximateTokenEstimator();
        var service = new AgentDialogService(dialogs, deepSeek, histories, estimator,
                new AgentSummaryStore(directory.resolve("summary"), json),
                new ConversationSummaryService(deepSeek, estimator),
                new StickyFactsStore(directory.resolve("facts"), json),
                new StickyFactsService(deepSeek, estimator, json),
                new AgentBranchStore(directory.resolve("branches"), json),
                new AgentMemoryStore(directory.resolve("memory"), json), 0, models);
        UUID dialog = UUID.fromString(dialogs.create().id());
        UUID task = UUID.randomUUID();
        service.upsertMemory(dialog, task, AgentMemory.Scope.WORKING, "database", "PostgreSQL");
        when(deepSeek.complete(anyList(), eq(AgentConfig.defaults().model()), isNull(), isNull()))
                .thenReturn(new DeepSeekClient.Completion("DeepSeek answer", "stop", null));
        service.reply(dialog, "default", ContextMode.FULL, 4, null, task, null);
        service.reply(dialog, "explicit", ContextMode.FULL, 4, null, task, AgentModelCatalog.DEFAULT_KEY);
        verify(deepSeek, times(2)).complete(anyList(), eq(AgentConfig.defaults().model()), isNull(), isNull());
        var result = mock(OpenAiResponsesClient.Result.class);
        when(result.analysis()).thenReturn("OpenAI answer");
        when(result.usage()).thenReturn(new ModelProfile.Usage(10L, 5L, 15L, null, null, null));
        when(openAi.complete(any(), anyList(), isNull(), isNull())).thenReturn(result);
        for (var model : ModelProfile.MODELS) {
            var reply = service.reply(dialog, model.key(), ContextMode.SLIDING_WINDOW, 2, null, task, model.key());
            assertEquals("OpenAI answer", reply.analysis());
            assertEquals(15L, reply.metrics().providerUsage().totalTokens());
            verify(openAi).complete(eq(model), argThat(messages -> messages.size() == 5
                    && messages.get(0).role().equals("system")
                    && messages.get(3).content().contains("PostgreSQL")
                    && messages.get(4).content().equals(model.key())), isNull(), isNull());
        }
        assertEquals(11, histories.load(dialog).orElseThrow().size());
        assertTrue(histories.load(dialog).orElseThrow().stream().noneMatch(m -> m.content().contains("PostgreSQL")));
        assertThrows(IllegalArgumentException.class,
                () -> service.reply(dialog, "rejected", ContextMode.FULL, 4, null, task, "arbitrary-model"));
        assertEquals(11, histories.load(dialog).orElseThrow().size());
        verifyNoMoreInteractions(openAi, deepSeek);
        reset(openAi);
        when(openAi.complete(any(), anyList(), isNull(), isNull())).thenAnswer(call -> {
            List<ConversationContext.Message> messages = call.getArgument(1);
            var completion = mock(OpenAiResponsesClient.Result.class);
            when(completion.analysis()).thenReturn(messages.getFirst().content().contains("Extract important")
                    ? "{\"facts\":{\"project\":\"Helios\"}}" : "chosen model answer");
            when(completion.usage()).thenReturn(new ModelProfile.Usage(10L, 5L, 15L, null, null, null));
            return completion;
        });
        var sticky = service.reply(dialog, "sticky", ContextMode.STICKY_FACTS, 2, null, task, "WEAK");
        assertEquals("Helios", sticky.contextMetadata().facts().facts().get("project"));
        assertEquals(6, sticky.factsMetrics().size());
        assertEquals("chosen model answer", sticky.analysis());
        var summary = service.reply(dialog, "summary", ContextMode.SUMMARY_RECENT, 2, null, task, "WEAK");
        assertNotNull(summary.summaryMetrics());
        assertEquals("chosen model answer", summary.contextMetadata().summary());
        verify(openAi, times(9)).complete(eq(ModelProfile.resolve("WEAK")), anyList(), isNull(), isNull());
        verifyNoMoreInteractions(deepSeek);
    }

    @Test
    void catalogContainsOnlySafeSelectionAndPresentationData() throws Exception {
        var catalog = new AgentModelCatalog(new DeepSeekAgentModelExecutor(mock(DeepSeekClient.class)),
                new OpenAiAgentModelExecutor(mock(OpenAiResponsesClient.class)));
        assertEquals(AgentModelCatalog.DEFAULT_KEY, catalog.resolve(null).option().key());
        assertEquals(AgentConfig.defaults().model(), catalog.resolve(AgentModelCatalog.DEFAULT_KEY).option().label());
        assertEquals(List.of("DEEPSEEK", "WEAK", "MEDIUM", "STRONG"), catalog.options().stream().map(AgentModelCatalog.Option::key).toList());
        assertThrows(IllegalArgumentException.class, () -> catalog.resolve(""));
        var node = new ObjectMapper().valueToTree(catalog.options());
        assertEquals(3, node.get(0).size());
    }
}
