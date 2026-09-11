package dev.aiadvent.mentor;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
class AgentDialogService {
    private final DialogStore dialogs;
    private final DeepSeekClient client;
    private final AgentHistoryStore histories;
    private final ApproximateTokenEstimator tokenEstimator;
    private final AgentConfig defaultConfig;
    private final AgentSummaryStore summaries;
    private final ConversationSummaryService summaryService;
    private final ConcurrentHashMap<UUID, EngineeringReviewAgent> agents = new ConcurrentHashMap<>();

    AgentDialogService(DialogStore dialogs, DeepSeekClient client, AgentHistoryStore histories,
                       ApproximateTokenEstimator tokenEstimator,
                       AgentSummaryStore summaries, ConversationSummaryService summaryService,
                       @Value("${mentor.agent.context-token-limit:0}") int contextTokenLimit) {
        this.dialogs = dialogs;
        this.client = client;
        this.histories = histories;
        this.tokenEstimator = tokenEstimator;
        this.summaries = summaries;
        this.summaryService = summaryService;
        this.defaultConfig = AgentConfig.defaults(contextTokenLimit == 0 ? null : contextTokenLimit);
    }

    AgentReply reply(UUID dialogId, String input, ContextMode mode, Integer recentMessageCount)
            throws IOException, DeepSeekException {
        EngineeringReviewAgent agent = agents.get(dialogId);
        if (agent == null) {
            dialogs.load(dialogId.toString());
            ConversationContext context = histories.load(dialogId)
                    .map(ConversationContext::new)
                    .orElseGet(() -> new ConversationContext(defaultConfig.systemPrompt()));
            EngineeringReviewAgent created = new EngineeringReviewAgent(dialogId, defaultConfig, context,
                    client, histories, tokenEstimator, summaries, summaryService,
                    new FullContextPolicy(), new SummaryRecentContextPolicy());
            EngineeringReviewAgent existing = agents.putIfAbsent(dialogId, created);
            agent = existing == null ? created : existing;
        }
        return agent.reply(input, mode, recentMessageCount);
    }

    AgentReply reply(UUID dialogId, String input) throws IOException, DeepSeekException {
        return reply(dialogId, input, ContextMode.FULL, 4);
    }
}
