package dev.aiadvent.worker.api;

import dev.aiadvent.worker.agent.AgentDialogService;
import dev.aiadvent.worker.agent.InvariantGuard;
import dev.aiadvent.worker.agent.OrchestrationException;
import dev.aiadvent.worker.agent.OrchestrationTrace;
import dev.aiadvent.worker.agent.TokenMetrics;
import dev.aiadvent.worker.mcp.OrchestrationTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentOrchestrationInvariantControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AgentDialogService agents;
    @MockitoBean private OrchestrationTools orchestration;

    private final OrchestrationTrace trace = new OrchestrationTrace("turn-1", "SUCCESS",
            List.of(new OrchestrationTrace.Step(1, "workspace", "search_repository", "SUCCESS", null, null)));
    private final TokenMetrics metrics = new TokenMetrics(2, 3, 4, null);

    @Test
    void conflictPreservesInvariantErrorAndTrace() throws Exception {
        UUID invariantId = UUID.randomUUID();
        var outcome = new InvariantGuard.Outcome(InvariantGuard.Decision.CONFLICT, invariantId,
                "Violates rule.", "Keep PostgreSQL.");
        stub(new OrchestrationException("INVARIANT_CONFLICT", trace,
                new InvariantGuard.RejectedCandidateException(outcome, "Persistence", metrics)));

        request().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVARIANT_CONFLICT"))
                .andExpect(jsonPath("$.invariantId").value(invariantId.toString()))
                .andExpect(jsonPath("$.invariantName").value("Persistence"))
                .andExpect(jsonPath("$.explanation").value("Violates rule."))
                .andExpect(jsonPath("$.compatibleContinuation").value("Keep PostgreSQL."))
                .andExpect(jsonPath("$.guardMetrics.responseTokens").value(4))
                .andExpect(jsonPath("$.orchestrationTrace.steps[0].tool").value("search_repository"));
    }

    @Test
    void uncertainPreservesInvariantErrorAndTrace() throws Exception {
        UUID invariantId = UUID.randomUUID();
        var outcome = new InvariantGuard.Outcome(InvariantGuard.Decision.UNCERTAIN, invariantId,
                "Insufficient evidence.", "Inspect source first.");
        stub(new OrchestrationException("INVARIANT_UNCERTAIN", trace,
                new InvariantGuard.RejectedCandidateException(outcome, "Persistence", metrics)));

        request().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVARIANT_UNCERTAIN"))
                .andExpect(jsonPath("$.invariantId").value(invariantId.toString()))
                .andExpect(jsonPath("$.invariantName").value("Persistence"))
                .andExpect(jsonPath("$.explanation").value("Insufficient evidence."))
                .andExpect(jsonPath("$.compatibleContinuation").value("Inspect source first."))
                .andExpect(jsonPath("$.guardMetrics.responseTokens").value(4))
                .andExpect(jsonPath("$.orchestrationTrace.steps[0].tool").value("search_repository"));
    }

    @Test
    void guardFailurePreservesErrorMetricsAndTrace() throws Exception {
        stub(new OrchestrationException("INVARIANT_CHECK_FAILED", trace,
                new InvariantGuard.GuardFailureException("Invariant guard returned an invalid assessment.",
                        new IllegalArgumentException("invalid"), metrics)));

        request().andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INVARIANT_CHECK_FAILED"))
                .andExpect(jsonPath("$.explanation").value("Invariant guard returned an invalid assessment."))
                .andExpect(jsonPath("$.guardMetrics.responseTokens").value(4))
                .andExpect(jsonPath("$.orchestrationTrace.steps[0].tool").value("search_repository"));
    }

    private void stub(OrchestrationException error) throws Exception {
        when(orchestration.ready()).thenReturn(true);
        when(agents.replyWithOrchestration(any(), any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any())).thenThrow(error);
    }

    private org.springframework.test.web.servlet.ResultActions request() throws Exception {
        return mvc.perform(post("/api/dialogs/{id}/agent/messages", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"input\":\"check\",\"useMcpOrchestration\":true}"));
    }
}
