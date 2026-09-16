package dev.aiadvent.mentor;

import dev.aiadvent.mentor.model.ModelTestFixtures;

import dev.aiadvent.mentor.model.AgentModelCatalog;
import dev.aiadvent.mentor.model.AgentModelMessage;
import dev.aiadvent.mentor.model.AgentModelRequest;
import dev.aiadvent.mentor.model.DeepSeekTransport;
import dev.aiadvent.mentor.model.ModelProfile;
import dev.aiadvent.mentor.model.OpenAiResponsesClient;
import dev.aiadvent.mentor.model.ProviderUsage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentModelsTest {
    @TempDir Path directory;

    @Test
    void selectionsReachTheSameAgentHistoryAndScopedMemory() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        var dialogs = new DialogStore(directory.resolve("dialogs"), json);
        var histories = new AgentHistoryStore(directory.resolve("history"), json);
        var deepSeek = mock(DeepSeekTransport.class);
        var openAi = mock(OpenAiResponsesClient.class);
        var deepSeekExecutor = spy(ModelTestFixtures.deepSeekExecutor(deepSeek));
        var openAiExecutor = spy(ModelTestFixtures.openAiExecutor(openAi));
        var models = new AgentModelCatalog(deepSeekExecutor, openAiExecutor);
        var estimator = new ApproximateTokenEstimator();
        var service = new AgentDialogService(dialogs, histories, estimator,
                new AgentSummaryStore(directory.resolve("summary"), json),
                new ConversationSummaryService(deepSeekExecutor, estimator),
                new StickyFactsStore(directory.resolve("facts"), json),
                new StickyFactsService(deepSeekExecutor, estimator, json),
                new AgentBranchStore(directory.resolve("branches"), json),
                new AgentMemoryStore(directory.resolve("memory"), json), 0, models);
        UUID dialog = UUID.fromString(dialogs.create().id());
        UUID task = UUID.randomUUID();
        service.upsertMemory(dialog, task, AgentMemory.Scope.WORKING, "database", "PostgreSQL");
        when(ModelTestFixtures.complete(deepSeek, any(AgentModelRequest.class)))
                .thenReturn(new DeepSeekTransport.Completion("DeepSeek answer", "stop", null));
        var defaultReply = service.reply(dialog, "default", ContextMode.FULL, 4, null, task, null);
        assertEquals("DeepSeek answer", defaultReply.analysis());
        assertEquals("DeepSeek answer",
                service.reply(dialog, "explicit", ContextMode.FULL, 4, null, task, AgentModelCatalog.DEFAULT_KEY).analysis());
        ArgumentCaptor<AgentModelRequest> deepSeekRequests = ArgumentCaptor.forClass(AgentModelRequest.class);
        ModelTestFixtures.complete(verify(deepSeek, times(2)), deepSeekRequests.capture());
        for (AgentModelRequest request : deepSeekRequests.getAllValues()) {
            assertEquals(AgentConfig.defaults().model(), request.model());
            assertNull(request.temperature());
            assertNull(request.maxTokens());
            assertEquals(AgentConfig.defaults().systemPrompt(), request.messages().getFirst().content());
            assertEquals("system", request.messages().getFirst().role());
        }
        var firstRequest = deepSeekRequests.getAllValues().get(0);
        var secondRequest = deepSeekRequests.getAllValues().get(1);
        assertEquals(List.of("system", "user", "user"),
                firstRequest.messages().stream().map(AgentModelMessage::role).toList());
        assertEquals(List.of("system", "user", "assistant", "user", "user"),
                secondRequest.messages().stream().map(AgentModelMessage::role).toList());
        assertTrue(firstRequest.messages().get(1).content().contains("PostgreSQL"));
        assertEquals("default", firstRequest.messages().getLast().content());
        assertEquals(firstRequest.messages().getFirst(), secondRequest.messages().getFirst());
        assertEquals(firstRequest.messages().getLast(), secondRequest.messages().get(1));
        assertEquals(new AgentModelMessage("assistant", "DeepSeek answer"), secondRequest.messages().get(2));
        assertEquals(firstRequest.messages().get(1), secondRequest.messages().get(3));
        assertEquals("explicit", secondRequest.messages().getLast().content());
        verify(deepSeekExecutor, times(2)).complete(deepSeekRequests.capture());
        var result = mock(OpenAiResponsesClient.Result.class);
        when(result.analysis()).thenReturn("OpenAI answer");
        when(result.usage()).thenReturn(new ModelProfile.Usage(10L, 5L, 15L, null, null, null));
        when(ModelTestFixtures.complete(openAi, any(), anyList(), isNull(), isNull())).thenReturn(result);
        for (var model : ModelProfile.MODELS) {
            var reply = service.reply(dialog, model.key(), ContextMode.SLIDING_WINDOW, 2, null, task, model.key());
            assertEquals("OpenAI answer", reply.analysis());
            assertEquals(15L, reply.metrics().providerUsage().totalTokens());
            ArgumentCaptor<AgentModelRequest> selectedRequest = ArgumentCaptor.forClass(AgentModelRequest.class);
            verify(openAiExecutor, times(ModelProfile.MODELS.indexOf(model) + 1)).complete(selectedRequest.capture());
            var request = selectedRequest.getValue();
            assertEquals(model.key(), request.model());
            assertNull(request.temperature());
            assertNull(request.maxTokens());
            assertEquals(List.of("system", "user", "assistant", "user", "user"),
                    request.messages().stream().map(AgentModelMessage::role).toList());
            assertEquals(AgentConfig.defaults().systemPrompt(), request.messages().getFirst().content());
            int modelIndex = ModelProfile.MODELS.indexOf(model);
            assertEquals(modelIndex == 0 ? "explicit" : ModelProfile.MODELS.get(modelIndex - 1).key(),
                    request.messages().get(1).content());
            assertEquals(modelIndex == 0 ? "DeepSeek answer" : "OpenAI answer",
                    request.messages().get(2).content());
            assertEquals(firstRequest.messages().get(1), request.messages().get(3));
            assertEquals(model.key(), request.messages().getLast().content());
            ModelTestFixtures.complete(verify(openAi), model, request.messages(), null, null);
        }
        assertEquals(11, histories.load(dialog).orElseThrow().size());
        assertTrue(histories.load(dialog).orElseThrow().stream().noneMatch(m -> m.content().contains("PostgreSQL")));
        assertThrows(IllegalArgumentException.class,
                () -> service.reply(dialog, "rejected", ContextMode.FULL, 4, null, task, "arbitrary-model"));
        assertEquals(11, histories.load(dialog).orElseThrow().size());
        verifyNoMoreInteractions(openAi, deepSeek);
        reset(openAi);
        when(ModelTestFixtures.complete(openAi, any(), anyList(), isNull(), isNull())).thenAnswer(call -> {
            List<AgentModelMessage> messages = call.getArgument(1);
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
        ModelTestFixtures.complete(verify(openAi, times(9)), eq(ModelProfile.resolve("WEAK")), anyList(), isNull(), isNull());
        verifyNoMoreInteractions(deepSeek);
    }

    @Test
    void springWiresDeepSeekAsTheDefaultExecutorForCatalogAndMaintenance() throws Exception {
        var transport = mock(DeepSeekTransport.class);
        var usage = new ProviderUsage(10L, 5L, 15L);
        when(ModelTestFixtures.complete(transport, any(AgentModelRequest.class))).thenReturn(
                new DeepSeekTransport.Completion("summary", "stop", usage),
                new DeepSeekTransport.Completion("{\"facts\":{\"project\":\"Helios\"}}", "stop", usage));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DeepSeekTransport.class, () -> transport);
            context.registerBean(OpenAiResponsesClient.class, () -> mock(OpenAiResponsesClient.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            ModelTestFixtures.registerExecutors(context);
            context.register(
                    AgentModelCatalog.class, ApproximateTokenEstimator.class,
                    ConversationSummaryService.class, StickyFactsService.class);
            context.refresh();
            assertEquals(1, context.getBeansOfType(DeepSeekTransport.class).size());
            var catalog = context.getBean(AgentModelCatalog.class);
            assertSame(context.getBean("deepSeekAgentModelExecutor"), catalog.resolve(null).executor());
            assertSame(context.getBean("openAiAgentModelExecutor"), catalog.resolve("WEAK").executor());
            var config = new AgentConfig("deepseek-v4-flash", "system", 0.7, 450, null);
            var summary = context.getBean(ConversationSummaryService.class).generate(
                    List.of(new ConversationContext.Message("user", "source")), null, config, 1);
            assertEquals("summary", summary.summary().summary());
            assertEquals(usage, summary.metrics().providerUsage());
            var facts = context.getBean(StickyFactsService.class).update(StickyFacts.empty(), "input", config, 1);
            assertEquals("Helios", facts.facts().facts().get("project"));
            assertEquals(usage, facts.metrics().providerUsage());
            ArgumentCaptor<AgentModelRequest> requests = ArgumentCaptor.forClass(AgentModelRequest.class);
            ModelTestFixtures.complete(verify(transport, times(2)), requests.capture());
            for (var request : requests.getAllValues()) {
                assertEquals(config.model(), request.model());
                assertEquals(config.temperature(), request.temperature());
                assertEquals(config.maxTokens(), request.maxTokens());
                assertEquals(List.of("system", "user"),
                        request.messages().stream().map(AgentModelMessage::role).toList());
            }
        }
    }

}
