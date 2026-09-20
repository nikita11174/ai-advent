package dev.aiadvent.worker.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.context.ApproximateTokenEstimator;
import dev.aiadvent.worker.invariant.Invariant;
import dev.aiadvent.worker.model.AgentModelExecutor;
import dev.aiadvent.worker.model.AgentModelMessage;
import dev.aiadvent.worker.model.AgentModelRequest;
import dev.aiadvent.worker.model.ModelExecutionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class InvariantGuard {
    private final ObjectMapper json;
    private final ApproximateTokenEstimator tokenEstimator;

    public InvariantGuard(ObjectMapper json, ApproximateTokenEstimator tokenEstimator) {
        this.json = json;
        this.tokenEstimator = tokenEstimator;
    }

    public Assessment assess(String input, String candidate, List<Invariant> invariants, AgentModelExecutor executor,
                          AgentConfig config) throws ModelExecutionException {
        if (invariants.isEmpty()) {
            return new Assessment(new Outcome(Decision.ALLOW, null, "", ""), null);
        }
        String rules = invariants.stream().map(InvariantGuard::render).reduce((left, right) -> left + "\n" + right).orElseThrow();
        List<AgentModelMessage> messages = List.of(
                new AgentModelMessage("system", """
                        Assess whether the candidate assistant response violates any mandatory invariant.
                        Return JSON only: {\"decision\":\"ALLOW|CONFLICT|UNCERTAIN\",\"invariantId\":\"UUID or null\",\"explanation\":\"short text\",\"compatibleContinuation\":\"short text\"}.
                        ALLOW requires no violation. CONFLICT requires exactly one listed invariantId. UNCERTAIN is for insufficient certainty.
                        Mandatory invariants:\n%s""".formatted(rules)),
                new AgentModelMessage("user", """
                        User request:\n%s\n\nCandidate assistant response:\n%s""".formatted(input, candidate))
        );
        AgentModelRequest request = new AgentModelRequest(messages, config.model(), 0.0, config.maxTokens());
        String content;
        TokenMetrics metrics;
        try {
            var completion = executor.complete(request);
            content = completion.content();
            metrics = new TokenMetrics(tokenEstimator.estimateText(messages.get(1).content()),
                    messages.stream().mapToLong(message -> tokenEstimator.estimateText(message.role())
                            + tokenEstimator.estimateText(message.content()) + 1).sum(),
                    tokenEstimator.estimateText(content), completion.usage());
        } catch (ModelExecutionException exception) {
            throw new GuardFailureException("Invariant guard failed.", exception, null);
        }
        try {
            GuardResponse response = json.readValue(content, GuardResponse.class);
            if (response == null || response.decision() == null) {
                throw new IllegalArgumentException("Assessment decision is required.");
            }
            Decision decision = Decision.valueOf(response.decision());
            UUID invariantId = response.invariantId() == null || response.invariantId().isBlank()
                    ? null : UUID.fromString(response.invariantId());
            if (decision == Decision.ALLOW && invariantId != null) {
                throw new IllegalArgumentException("ALLOW must not identify an invariant.");
            }
            if (decision == Decision.CONFLICT && (invariantId == null || invariants.stream().noneMatch(rule -> rule.id().equals(invariantId)))) {
                throw new IllegalArgumentException("Conflict must identify an effective invariant.");
            }
            if (decision == Decision.UNCERTAIN && invariantId != null
                    && invariants.stream().noneMatch(rule -> rule.id().equals(invariantId))) {
                throw new IllegalArgumentException("Uncertain assessment must identify an effective invariant when present.");
            }
            if (decision != Decision.ALLOW && (response.explanation() == null || response.explanation().isBlank()
                    || response.compatibleContinuation() == null || response.compatibleContinuation().isBlank())) {
                throw new IllegalArgumentException("Rejected candidate requires explanation and compatible continuation.");
            }
            return new Assessment(new Outcome(decision, invariantId, blankWhenMissing(response.explanation()),
                    blankWhenMissing(response.compatibleContinuation())), metrics);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new GuardFailureException("Invariant guard returned an invalid assessment.", exception, metrics);
        }
    }

    private static String render(Invariant invariant) {
        return "[%s] %s: %s".formatted(invariant.id(), invariant.name(), invariant.rule());
    }

    private static String blankWhenMissing(String value) {
        return value == null ? "" : value.trim();
    }

    public enum Decision {
        ALLOW,
        CONFLICT,
        UNCERTAIN
    }

    public record Outcome(Decision decision, UUID invariantId, String explanation, String compatibleContinuation) {
    }

    public record Assessment(Outcome outcome, TokenMetrics metrics) {
    }

    private record GuardResponse(String decision, String invariantId, String explanation, String compatibleContinuation) {
    }

    public static class RejectedCandidateException extends IllegalStateException {
        private final Outcome outcome;
        private final String invariantName;

        private final TokenMetrics guardMetrics;

        public RejectedCandidateException(Outcome outcome, String invariantName, TokenMetrics guardMetrics) {
            super(outcome.explanation());
            this.outcome = outcome;
            this.invariantName = invariantName;
            this.guardMetrics = guardMetrics;
        }

        public Outcome outcome() {
            return outcome;
        }

        public String invariantName() {
            return invariantName;
        }

        public TokenMetrics guardMetrics() {
            return guardMetrics;
        }
    }

    public static class GuardFailureException extends ModelExecutionException {
        private final TokenMetrics guardMetrics;

        public GuardFailureException(String message, Throwable cause, TokenMetrics guardMetrics) {
            super(message, null, cause);
            this.guardMetrics = guardMetrics;
        }

        public TokenMetrics guardMetrics() {
            return guardMetrics;
        }
    }
}
