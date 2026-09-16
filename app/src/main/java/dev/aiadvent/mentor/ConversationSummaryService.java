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

    ConversationSummaryService(DeepSeekClient client, ApproximateTokenEstimator estimator) {
        this.defaultExecutor = new DeepSeekAgentModelExecutor(client);
        this.estimator = estimator;
    }

    SummaryGeneration generate(List<ConversationContext.Message> olderMessages,
                               ConversationSummary previous, AgentConfig config, int coveredCount)
            throws DeepSeekException {
        return generate(olderMessages, previous, config, coveredCount, defaultExecutor);
    }

    SummaryGeneration generate(List<ConversationContext.Message> olderMessages,
                               ConversationSummary previous, AgentConfig config, int coveredCount,
                               AgentModelExecutor executor) throws DeepSeekException {
        String source = olderMessages.stream()
                .map(message -> message.role() + ": " + message.content())
                .collect(Collectors.joining("\n\n"));
        String previousText = previous == null ? "(none)" : previous.summary();
        String request = "Previous summary:\n" + previousText + "\n\nRaw messages to incorporate:\n" + source;
        List<ConversationContext.Message> prompt = List.of(
                new ConversationContext.Message("system", SUMMARY_SYSTEM_PROMPT),
                new ConversationContext.Message("user", request));
        long contextTokens = estimator.estimateMessagesWithinLimit(prompt, config.contextTokenLimit());
        AgentModelExecutor.Completion completion = executor.complete(prompt, config);
        TokenMetrics metrics = new TokenMetrics(estimator.estimateText(request), contextTokens,
                estimator.estimateText(completion.content()), completion.usage());
        return new SummaryGeneration(new ConversationSummary(coveredCount, completion.content()), metrics);
    }
}
