package dev.aiadvent.mentor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
class ConversationSummaryService {
    private static final String SUMMARY_SYSTEM_PROMPT = """
            Summarize the engineering conversation for a future reviewer.
            Preserve requirements, decisions, facts, risks and unresolved questions.
            Return only a concise Markdown summary in Russian.
            """;

    private final AgentModelExecutor defaultExecutor;
    private final ApproximateTokenEstimator estimator;

    ConversationSummaryService(@org.springframework.beans.factory.annotation.Qualifier("deepSeekAgentModelExecutor")
                               AgentModelExecutor defaultExecutor, ApproximateTokenEstimator estimator) {
        this.defaultExecutor = defaultExecutor;
        this.estimator = estimator;
    }

    SummaryGeneration generate(List<ConversationContext.Message> olderMessages,
                               ConversationSummary previous, AgentConfig config, int coveredCount)
            throws ModelExecutionException {
        return generate(olderMessages, previous, config, coveredCount, defaultExecutor);
    }

    SummaryGeneration generate(List<ConversationContext.Message> olderMessages,
                               ConversationSummary previous, AgentConfig config, int coveredCount,
                               AgentModelExecutor executor) throws ModelExecutionException {
        String source = olderMessages.stream()
                .map(message -> message.role() + ": " + message.content())
                .collect(Collectors.joining("\n\n"));
        String previousText = previous == null ? "(none)" : previous.summary();
        String request = "Previous summary:\n" + previousText + "\n\nRaw messages to incorporate:\n" + source;
        List<ConversationContext.Message> prompt = List.of(
                new ConversationContext.Message("system", SUMMARY_SYSTEM_PROMPT),
                new ConversationContext.Message("user", request));
        long contextTokens = estimator.estimateMessagesWithinLimit(prompt, config.contextTokenLimit());
        AgentModelExecutor.Completion completion = executor.complete(toModelRequest(prompt, config));
        TokenMetrics metrics = new TokenMetrics(estimator.estimateText(request), contextTokens,
                estimator.estimateText(completion.content()), completion.usage());
        return new SummaryGeneration(new ConversationSummary(coveredCount, completion.content()), metrics);
    }

    private static AgentModelRequest toModelRequest(List<ConversationContext.Message> messages, AgentConfig config) {
        return new AgentModelRequest(messages.stream()
                .map(message -> new AgentModelMessage(message.role(), message.content()))
                .toList(), config.model(), config.temperature(), config.maxTokens());
    }
}
