package dev.aiadvent.mentor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
@Import(AgentDialogService.class)
@TestPropertySource(properties = "mentor.agent.context-token-limit=1")
class AgentControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private DialogStore store;
    @MockitoBean
    private DeepSeekClient client;
    @MockitoBean
    private AgentHistoryStore histories;
    @MockitoBean
    private ApproximateTokenEstimator tokenEstimator;

    @Test
    void returnsAnalysisAndRejectsAnOverlappingRequest() throws Exception {
        String route = "/api/dialogs/" + UUID.randomUUID() + "/agent/messages";
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.complete(anyList(), anyString(), isNull(), isNull())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new DeepSeekClient.Completion("answer", "stop", null);
        });
        var first = new FutureTask<>(() -> mvc.perform(post(route).contentType("application/json")
                .content("{\"input\":\"first\"}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"analysis\":\"answer\"}")));
        Thread thread = Thread.ofPlatform().start(first);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            mvc.perform(post(route).contentType("application/json").content("{\"input\":\"second\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").isNotEmpty());
        } finally {
            release.countDown();
            thread.join(5000);
        }
        first.get(5, TimeUnit.SECONDS);
        verify(client).complete(anyList(), anyString(), isNull(), isNull());
    }

    @Test
    void rejectsInvalidAndUnknownDialogsBeforeCallingProvider() throws Exception {
        String id = UUID.randomUUID().toString();
        String route = "/api/dialogs/" + id + "/agent/messages";
        for (String body : new String[]{"{}", "{\"input\":null}", "{\"input\":\"  \"}"}) {
            mvc.perform(post(route).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/dialogs/invalid/agent/messages").contentType("application/json")
                .content("{\"input\":\"hello\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(store, client);
        when(store.load(id)).thenThrow(new DialogStore.DialogNotFoundException(id));
        mvc.perform(post(route).contentType("application/json").content("{\"input\":\"hello\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").isNotEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void mapsStorageAndProviderFailures() throws Exception {
        String missing = UUID.randomUUID().toString();
        when(store.load(missing)).thenThrow(new IOException("unavailable"));
        mvc.perform(post("/api/dialogs/" + missing + "/agent/messages").contentType("application/json")
                .content("{\"input\":\"hello\"}"))
                .andExpect(status().isInternalServerError());
        verifyNoInteractions(client);
        when(client.complete(anyList(), anyString(), isNull(), isNull())).thenThrow(new DeepSeekException("Unavailable"));
        mvc.perform(post("/api/dialogs/" + UUID.randomUUID() + "/agent/messages").contentType("application/json")
                .content("{\"input\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("{\"error\":\"Unavailable\",\"rawResponse\":null}"));
    }

    @Test
    void mapsAnEstimatedContextOverflowBeforeCallingTheProvider() throws Exception {
        when(tokenEstimator.estimateMessages(anyList())).thenReturn(2L);

        mvc.perform(post("/api/dialogs/" + UUID.randomUUID() + "/agent/messages").contentType("application/json")
                .content("{\"input\":\"hello\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("Estimated context limit exceeded: 2 > 1."));

        verifyNoInteractions(client);
        verify(histories).load(any());
        verify(histories, never()).save(any(), anyList());
    }
}
