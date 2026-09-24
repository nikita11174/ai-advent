package dev.aiadvent.worker.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.context.*;
import dev.aiadvent.worker.dialog.*;
import dev.aiadvent.worker.mcp.WorkspaceToolRuntime;
import dev.aiadvent.worker.memory.*;
import dev.aiadvent.worker.model.*;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.*;
import dev.aiadvent.worker.workspace.monitor.MonitorState;
import dev.aiadvent.worker.workspace.monitor.MonitorStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkspaceMonitorAgentReadTest {
    @TempDir Path base;

    @Test void explicitReadUsesRealPersistedMcpSummaryAndKeepsCanonicalPair() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        Process git = new ProcessBuilder("git", "init", "-q", "-b", "main", repo.toString()).start();
        assertEquals(0, git.waitFor());
        Path stateDir = base.resolve("state");
        var store = new MonitorStateStore(repo, stateDir);
        store.save(store.load().withCommand(true, 10, Instant.now().minusSeconds(1),
                new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "START", 10, 1)));
        try (var runtime = new WorkspaceToolRuntime(false, repo.toString(), true, stateDir.toString(), true, "127.0.0.1")) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            while (runtime.getMonitor().path("aggregate").path("successCount").asInt() == 0
                    && System.nanoTime() < deadline) Thread.sleep(30);
            assertEquals(1, runtime.getMonitor().path("aggregate").path("successCount").asInt());

            var nativeModel = mock(ToolCapableModelExecutor.class, withSettings().extraInterfaces(AgentModelExecutor.class));
            var executor = (AgentModelExecutor) nativeModel;
            var prepared = mock(PreparedToolTurn.class);
            var continuation = mock(ToolContinuation.class);
            var second = mock(PreparedContinuation.class);
            when(prepared.estimatedInputTokens()).thenReturn(10L);
            when(second.estimatedInputTokens()).thenReturn(20L);
            when(nativeModel.prepareToolTurn(any(), any())).thenReturn(prepared);
            when(nativeModel.beginToolTurn(prepared)).thenReturn(new ToolRequestStep(
                    new ToolRequest("get_repository_monitor_summary", new ObjectMapper().readTree("{}")), continuation, null));
            when(nativeModel.prepareContinuation(any(), any())).thenReturn(second);
            when(nativeModel.continueToolTurn(second)).thenReturn(new FinalAnswer("One successful observation was recorded.", null));
            var context = new ConversationContext(AgentConfig.defaults().systemPrompt());
            var histories = mock(AgentHistoryStore.class);
            var agent = new ConversationAgent(UUID.randomUUID(), AgentConfig.defaults(), context, executor, histories,
                    new ApproximateTokenEstimator(), mock(AgentSummaryStore.class), mock(ConversationSummaryService.class),
                    mock(StickyFactsStore.class), mock(StickyFactsService.class), new FullContextPolicy(),
                    new SummaryRecentContextPolicy(), new SlidingWindowContextPolicy(), new StickyFactsContextPolicy(),
                    mock(AgentBranchStore.class), null, null, runtime);
            var reply = agent.replyWithTool("Explain the monitor", ContextMode.FULL, 4, AgentMemory.Snapshot.empty(null),
                    executor, AgentConfig.defaults(), null, null, List.of(), List.of(), false, true);
            assertEquals("One successful observation was recorded.", reply.analysis());
            var result = org.mockito.ArgumentCaptor.forClass(ToolResult.class);
            verify(nativeModel).prepareContinuation(any(), result.capture());
            assertEquals(1, result.getValue().structuredResult().path("aggregate").path("successCount").asInt());
            assertEquals(3, context.snapshot().size());
            assertFalse(context.snapshot().toString().contains("latestDigest"));
            verify(histories).save(any(), anyList());
        }
    }
}
