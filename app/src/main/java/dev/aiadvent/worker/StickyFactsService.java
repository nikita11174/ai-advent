package dev.aiadvent.worker;

import dev.aiadvent.worker.dialog.ConversationContext;
import dev.aiadvent.worker.memory.StickyFacts;
import dev.aiadvent.worker.context.ApproximateTokenEstimator;
import dev.aiadvent.worker.model.AgentModelExecutor;
import dev.aiadvent.worker.model.AgentModelMessage;
import dev.aiadvent.worker.model.AgentModelRequest;
import dev.aiadvent.worker.model.ModelExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

@Service
class StickyFactsService {
    private static final String FACTS_SYSTEM_PROMPT = """
            Extract important durable facts from the engineering conversation.
            Return only one JSON object with a single "facts" object containing concise string values.
            Preserve existing facts unless the new user message clearly changes them.
            Do not invent facts or include secrets.
            """;

    private final AgentModelExecutor defaultExecutor;
    private final ApproximateTokenEstimator estimator;
    private final ObjectMapper json;

    StickyFactsService(@org.springframework.beans.factory.annotation.Qualifier("deepSeekAgentModelExecutor")
                       AgentModelExecutor defaultExecutor, ApproximateTokenEstimator estimator, ObjectMapper json) {
        this.defaultExecutor = defaultExecutor;
        this.estimator = estimator;
        this.json = json;
    }

    StickyFactsGeneration update(StickyFacts current, String input, AgentConfig config, int coveredCount)
            throws ModelExecutionException {
        return update(current, input, config, coveredCount, defaultExecutor);
    }

    StickyFactsGeneration update(StickyFacts current, String input, AgentConfig config, int coveredCount,
                                AgentModelExecutor executor) throws ModelExecutionException {
        String existing = json.valueToTree(current.facts()).toString();
        String request = "Existing facts:\n" + existing + "\n\nNew user message:\n" + input;
        List<ConversationContext.Message> prompt = List.of(
                new ConversationContext.Message("system", FACTS_SYSTEM_PROMPT),
                new ConversationContext.Message("user", request));
        long contextTokens = estimator.estimateMessagesWithinLimit(prompt, config.contextTokenLimit());
        AgentModelExecutor.Completion completion = executor.complete(toModelRequest(prompt, config));
        StickyFacts facts = parse(completion.content(), coveredCount);
        TokenMetrics metrics = new TokenMetrics(estimator.estimateText(input), contextTokens,
                estimator.estimateText(completion.content()), completion.usage());
        return new StickyFactsGeneration(facts, metrics);
    }

    private StickyFacts parse(String content, int coveredCount) throws ModelExecutionException {
        try {
            JsonNode root = json.readTree(content);
            JsonNode factsNode = root == null ? null : root.path("facts");
            if (root == null || !root.isObject() || root.size() != 1 || !factsNode.isObject()) {
                throw new IllegalArgumentException("unexpected facts shape");
            }
            var facts = new LinkedHashMap<String, String>();
            var fields = factsNode.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!field.getValue().isTextual() || field.getValue().textValue().isBlank()) {
                    throw new IllegalArgumentException("fact values must be non-empty strings");
                }
                facts.put(field.getKey(), field.getValue().textValue());
            }
            return new StickyFacts(coveredCount, facts);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ModelExecutionException("DeepSeek returned malformed sticky facts.", content, exception);
        }
    }

    private static AgentModelRequest toModelRequest(List<ConversationContext.Message> messages, AgentConfig config) {
        return new AgentModelRequest(messages.stream()
                .map(message -> new AgentModelMessage(message.role(), message.content()))
                .toList(), config.model(), config.temperature(), config.maxTokens());
    }
}
