package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolRequest;
import dev.aiadvent.worker.agent.RepositoryEvidenceReader;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrchestrationToolsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final WorkspaceToolRuntime workspace = mock(WorkspaceToolRuntime.class);
    private final VerificationToolRuntime verification = mock(VerificationToolRuntime.class);
    private final OrchestrationTools tools = new OrchestrationTools(workspace, verification, mock(RepositoryEvidenceReader.class));

    @Test void registersTwoOwnersAndRejectsCollision() {
        assertEquals(4, tools.definitions().size());
        assertEquals("workspace", tools.server("search_repository"));
        assertEquals("verification", tools.server("find_allowed_tests"));
        var entries = new LinkedHashMap<String, OrchestrationTools.Entry>();
        OrchestrationTools.add(entries, "workspace", "shared", "one", Map.of("type", "object"));
        assertEquals("DUPLICATE_TOOL_NAME", assertThrows(IllegalStateException.class, () ->
                OrchestrationTools.add(entries, "verification", "shared", "two", Map.of("type", "object"))).getMessage());
    }

    @Test void unknownAndInvalidRequestsNeverReachEitherServer() throws Exception {
        assertEquals("UNKNOWN_TOOL", assertThrows(IllegalArgumentException.class, () ->
                tools.execute(new ToolRequest("made_up", json.readTree("{}")))).getMessage());
        assertEquals("INVALID_TOOL_ARGUMENTS", assertThrows(IllegalArgumentException.class, () ->
                tools.execute(new ToolRequest("run_allowed_test", json.readTree("{\"command\":\"mvn test\"}")))).getMessage());
        assertEquals("INVALID_TOOL_ARGUMENTS", assertThrows(IllegalArgumentException.class, () ->
                tools.execute(new ToolRequest("run_allowed_test", json.readTree("{\"testId\":\"anything\"}")))).getMessage());
        verifyNoInteractions(workspace, verification);
    }

    @Test void routesAllowedDiscoveryAndRunToVerificationOnly() throws Exception {
        when(verification.call(eq("find_allowed_tests"), anyMap()))
                .thenReturn(json.readTree("{\"checks\":[]}"));
        var result = tools.execute(new ToolRequest("find_allowed_tests", json.readTree("{\"concept\":\"branch\"}")));
        assertEquals(0, result.structuredResult().path("checks").size());
        verify(verification).call("find_allowed_tests", Map.of("concept", "branch"));
        verifyNoInteractions(workspace);
    }

    @Test void testFailureIsEvidenceButZeroTestsCannotPass() throws Exception {
        String base = "{\"runId\":\"run-1\",\"testId\":\"branch-preflight\",\"head\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
                + "\"dirty\":true,\"sourceHash\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\","
                + "\"toolExecutionStatus\":\"SUCCESS\",\"checkedBehavior\":\"unknown branch\",\"code\":\"TEST_FAIL\","
                + "\"tests\":1,\"status\":\"TEST_FAIL\"}";
        when(verification.call(eq("run_allowed_test"), anyMap())).thenReturn(json.readTree(base));
        var request = new ToolRequest("run_allowed_test", json.readTree("{\"testId\":\"branch-preflight\"}"));
        assertEquals("TEST_FAIL", tools.execute(request).structuredResult().path("status").asText());
        when(verification.call(eq("run_allowed_test"), anyMap())).thenReturn(json.readTree(
                base.replace("\"tests\":1,\"status\":\"TEST_FAIL\"", "\"tests\":0,\"status\":\"TEST_PASS\"")));
        assertEquals("INVALID_TOOL_RESULT", assertThrows(IllegalStateException.class,
                () -> tools.execute(request)).getMessage());
    }
}
