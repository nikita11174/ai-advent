package dev.aiadvent.mentor;

import java.util.concurrent.locks.ReentrantLock;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class EngineeringReviewAgent {
    private final AgentConfig config;
    private final ConversationContext context;
    private final DeepSeekClient client;
    private final UUID dialogId;
    private final AgentHistoryStore histories;
    private final ApproximateTokenEstimator tokenEstimator;
    private final AgentSummaryStore summaries;
    private final ConversationSummaryService summaryService;
    private final StickyFactsStore factsStore;
    private final StickyFactsService factsService;
    private final ContextPolicy fullPolicy;
    private final ContextPolicy summaryRecentPolicy;
    private final ContextPolicy slidingWindowPolicy;
    private final ContextPolicy stickyFactsPolicy;
    private final AgentBranchStore branches;
    private final String branchId;
    private final ReentrantLock turnLock = new ReentrantLock();

    EngineeringReviewAgent(UUID dialogId, AgentConfig config, ConversationContext context,
                           DeepSeekClient client, AgentHistoryStore histories, ApproximateTokenEstimator tokenEstimator,
                           AgentSummaryStore summaries, ConversationSummaryService summaryService,
                           StickyFactsStore factsStore, StickyFactsService factsService,
                           ContextPolicy fullPolicy, ContextPolicy summaryRecentPolicy,
                           ContextPolicy slidingWindowPolicy, ContextPolicy stickyFactsPolicy,
                           AgentBranchStore branches, String branchId) {
        this.dialogId = dialogId;
        this.config = config;
        this.context = context;
        this.client = client;
        this.histories = histories;
        this.tokenEstimator = tokenEstimator;
        this.summaries = summaries;
        this.summaryService = summaryService;
        this.factsStore = factsStore;
        this.factsService = factsService;
        this.fullPolicy = fullPolicy;
        this.summaryRecentPolicy = summaryRecentPolicy;
        this.slidingWindowPolicy = slidingWindowPolicy;
        this.stickyFactsPolicy = stickyFactsPolicy;
        this.branches = branches;
        this.branchId = branchId;
    }

    AgentReply reply(String input) throws IOException, DeepSeekException {
        return reply(input, ContextMode.FULL, 4);
    }

    AgentReply reply(String input, ContextMode mode, Integer recentMessageCount)
            throws IOException, DeepSeekException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input must not be empty.");
        }
        if (!turnLock.tryLock()) {
            throw new BusyException();
        }
        try {
            if (mode == null) {
                mode = ContextMode.FULL;
            }
            int recent = recentMessageCount == null ? 4 : recentMessageCount;
            if (recent < 1) {
                throw new IllegalArgumentException("recentMessageCount must be positive.");
            }
            List<ConversationContext.Message> raw = context.snapshot();
            ConversationSummary summary = null;
            TokenMetrics summaryMetrics = null;
            var factsMetrics = new ArrayList<TokenMetrics>();
            StickyFacts facts = null;
            boolean summaryIncluded = false;
            if (mode == ContextMode.SUMMARY_RECENT) {
                int committedCount = raw.size() - 1;
                int targetCoverage = Math.max(0, committedCount - recent);
                ConversationSummary stored = summaries.load(dialogId).orElse(null);
                if (targetCoverage > 0 && (stored == null || stored.summarizedMessageCount() != targetCoverage)) {
                    List<ConversationContext.Message> source = stored != null && stored.summarizedMessageCount() < targetCoverage
                            ? raw.subList(1 + stored.summarizedMessageCount(), 1 + targetCoverage)
                            : raw.subList(1, 1 + targetCoverage);
                    SummaryGeneration generation = summaryService.generate(source,
                            stored != null && stored.summarizedMessageCount() < targetCoverage ? stored : null,
                            config, targetCoverage);
                    summaries.save(dialogId, generation.summary());
                    stored = generation.summary();
                    summaryMetrics = generation.metrics();
                }
                summary = stored;
                summaryIncluded = targetCoverage > 0;
            }
            if (mode == ContextMode.STICKY_FACTS) {
                int committedUserMessages = (raw.size() - 1) / 2;
                StickyFacts stored = factsStore.load(dialogId).orElse(StickyFacts.empty());
                facts = reconcile(stored, raw, committedUserMessages, factsMetrics);
                StickyFactsGeneration update = factsService.update(facts, input, config,
                        committedUserMessages + 1);
                facts = update.facts();
                factsMetrics.add(update.metrics());
            }
            ContextPolicy policy = switch (mode) {
                case SUMMARY_RECENT -> summaryRecentPolicy;
                case SLIDING_WINDOW -> slidingWindowPolicy;
                case STICKY_FACTS -> stickyFactsPolicy;
                case FULL -> fullPolicy;
            };
            List<ConversationContext.Message> outbound = policy.build(raw, input, summary, facts, recent);
            long contextTokens = tokenEstimator.estimateMessages(outbound);
            if (config.contextTokenLimit() != null && contextTokens > config.contextTokenLimit()) {
                throw new ContextLimitExceededException(contextTokens, config.contextTokenLimit());
            }
            DeepSeekClient.Completion completion = client.complete(outbound, config.model(),
                    config.temperature(), config.maxTokens());
            String analysis = completion.content();
            List<ConversationContext.Message> completed = context.withCompletedTurn(input, analysis);
            if (branchId == null) {
                histories.save(dialogId, completed);
            } else {
                branches.saveBranch(dialogId, branchId, completed);
            }
            context.commit(completed);
            if (mode == ContextMode.STICKY_FACTS) {
                factsStore.save(dialogId, facts);
            }
            return new AgentReply(analysis, new TokenMetrics(tokenEstimator.estimateText(input), contextTokens,
                    tokenEstimator.estimateText(analysis), completion.usage()), summaryMetrics, List.copyOf(factsMetrics),
                    new ContextMetadata(mode, recent, summaryIncluded && summary != null ? summary.summary() : null,
                            summaryIncluded && summary != null ? summary.summarizedMessageCount() : 0, facts));
        } finally {
            turnLock.unlock();
        }
    }

    private StickyFacts reconcile(StickyFacts stored, List<ConversationContext.Message> raw,
                                  int committedUserMessages, List<TokenMetrics> metrics)
            throws DeepSeekException {
        StickyFacts facts = stored.coveredUserMessageCount() <= committedUserMessages
                ? stored : StickyFacts.empty();
        int start = facts.coveredUserMessageCount();
        for (int index = start; index < committedUserMessages; index++) {
            StickyFactsGeneration update = factsService.update(facts, raw.get(1 + index * 2).content(), config,
                    index + 1);
            facts = update.facts();
            metrics.add(update.metrics());
        }
        return facts;
    }

    static class BusyException extends RuntimeException {
        BusyException() {
            super("Agent is already processing a message in this dialog.");
        }
    }

    static class ContextLimitExceededException extends RuntimeException {
        ContextLimitExceededException(long contextTokens, int contextTokenLimit) {
            super("Estimated context limit exceeded: " + contextTokens + " > " + contextTokenLimit + ".");
        }
    }
}
