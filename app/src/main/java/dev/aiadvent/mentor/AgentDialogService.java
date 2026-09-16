package dev.aiadvent.mentor;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
class AgentDialogService {
    private final DialogStore dialogs;
    private final AgentHistoryStore histories;
    private final ApproximateTokenEstimator tokenEstimator;
    private final AgentConfig defaultConfig;
    private final AgentSummaryStore summaries;
    private final ConversationSummaryService summaryService;
    private final StickyFactsStore factsStore;
    private final StickyFactsService factsService;
    private final AgentBranchStore branches;
    private final AgentMemoryStore memories;
    private final AgentModelCatalog models;
    private final ConcurrentHashMap<AgentKey, EngineeringReviewAgent> agents = new ConcurrentHashMap<>();

    AgentDialogService(DialogStore dialogs, DeepSeekClient client, AgentHistoryStore histories,
                       ApproximateTokenEstimator tokenEstimator,
                       AgentSummaryStore summaries, ConversationSummaryService summaryService,
                       StickyFactsStore factsStore, StickyFactsService factsService,
                       AgentBranchStore branches, AgentMemoryStore memories,
                       @Value("${mentor.agent.context-token-limit:0}") int contextTokenLimit) {
        this(dialogs, client, histories, tokenEstimator, summaries, summaryService, factsStore, factsService,
                branches, memories, contextTokenLimit, new AgentModelCatalog(new DeepSeekAgentModelExecutor(client), null));
    }

    @org.springframework.beans.factory.annotation.Autowired
    AgentDialogService(DialogStore dialogs, DeepSeekClient client, AgentHistoryStore histories,
                       ApproximateTokenEstimator tokenEstimator, AgentSummaryStore summaries,
                       ConversationSummaryService summaryService, StickyFactsStore factsStore,
                       StickyFactsService factsService, AgentBranchStore branches, AgentMemoryStore memories,
                       @Value("${mentor.agent.context-token-limit:0}") int contextTokenLimit, AgentModelCatalog models) {
        this.dialogs = dialogs;
        this.histories = histories;
        this.tokenEstimator = tokenEstimator;
        this.summaries = summaries;
        this.summaryService = summaryService;
        this.factsStore = factsStore;
        this.factsService = factsService;
        this.branches = branches;
        this.memories = memories;
        this.models = models;
        this.defaultConfig = AgentConfig.defaults(contextTokenLimit == 0 ? null : contextTokenLimit);
    }

    AgentReply reply(UUID dialogId, String input, ContextMode mode, Integer recentMessageCount)
            throws IOException, DeepSeekException {
        return reply(dialogId, input, mode, recentMessageCount, null);
    }

    AgentReply reply(UUID dialogId, String input, ContextMode mode, Integer recentMessageCount, String branchId)
            throws IOException, DeepSeekException {
        return reply(dialogId, input, mode, recentMessageCount, branchId, null);
    }

    AgentReply reply(UUID dialogId, String input, ContextMode mode, Integer recentMessageCount, String branchId,
                     UUID taskId) throws IOException, DeepSeekException {
        return reply(dialogId, input, mode, recentMessageCount, branchId, taskId, null);
    }

    AgentReply reply(UUID dialogId, String input, ContextMode mode, Integer recentMessageCount, String branchId,
                     UUID taskId, String agentModelKey) throws IOException, DeepSeekException {
        AgentModelCatalog.Selection model = models.resolve(agentModelKey);
        if (branchId != null && (mode != null && mode != ContextMode.FULL)) {
            throw new IllegalArgumentException("Branch messages use FULL context only.");
        }
        AgentKey key = new AgentKey(dialogId, branchId);
        EngineeringReviewAgent agent = agents.get(key);
        if (agent == null) {
            dialogs.load(dialogId.toString());
            ConversationContext context;
            if (branchId == null) {
                context = histories.load(dialogId)
                        .map(ConversationContext::new)
                        .orElseGet(() -> new ConversationContext(defaultConfig.systemPrompt()));
            } else {
                context = new ConversationContext(branches.loadBranch(dialogId, branchId));
            }
            EngineeringReviewAgent created = new EngineeringReviewAgent(dialogId, defaultConfig, context,
                    models.resolve(null).executor(), histories, tokenEstimator, summaries, summaryService,
                    factsStore, factsService, new FullContextPolicy(), new SummaryRecentContextPolicy(),
                    new SlidingWindowContextPolicy(), new StickyFactsContextPolicy(), branches, branchId);
            EngineeringReviewAgent existing = agents.putIfAbsent(key, created);
            agent = existing == null ? created : existing;
        }
        AgentMemory.Snapshot memory = memories.load(dialogId, taskId);
        return agent.reply(input, mode, recentMessageCount, memory,
                model.executor(), defaultConfig.withModel(model.model()));
    }

    AgentReply reply(UUID dialogId, String input) throws IOException, DeepSeekException {
        return reply(dialogId, input, ContextMode.FULL, 4);
    }

    AgentBranchStore.Checkpoint createCheckpoint(UUID dialogId, String sourceBranchId) throws IOException {
        dialogs.load(dialogId.toString());
        List<ConversationContext.Message> history = sourceBranchId == null
                ? histories.load(dialogId).orElseGet(() -> new ConversationContext(defaultConfig.systemPrompt()).snapshot())
                : branches.loadBranch(dialogId, sourceBranchId);
        return branches.createCheckpoint(dialogId, history);
    }

    AgentBranchStore.Branch createBranch(UUID dialogId, String checkpointId) throws IOException {
        dialogs.load(dialogId.toString());
        return branches.createBranch(dialogId, checkpointId);
    }

    List<AgentBranchStore.Branch> branches(UUID dialogId) throws IOException {
        dialogs.load(dialogId.toString());
        return branches.branches(dialogId);
    }

    List<AgentBranchStore.Checkpoint> checkpoints(UUID dialogId) throws IOException {
        dialogs.load(dialogId.toString());
        return branches.checkpoints(dialogId);
    }

    AgentMemory.Snapshot memory(UUID dialogId, UUID taskId) throws IOException {
        dialogs.load(dialogId.toString());
        return memories.load(dialogId, taskId);
    }

    AgentMemory.Snapshot upsertMemory(UUID dialogId, UUID taskId, AgentMemory.Scope scope,
                                      String key, String value) throws IOException {
        dialogs.load(dialogId.toString());
        if (scope == null) {
            throw new IllegalArgumentException("Memory scope is required.");
        }
        if (scope == AgentMemory.Scope.WORKING && taskId == null) {
            throw new IllegalArgumentException("taskId is required for WORKING memory.");
        }
        AgentMemory.validateKey(key);
        AgentMemory.validateValue(value);
        return memories.upsert(dialogId, taskId, scope, key, value);
    }

    private record AgentKey(UUID dialogId, String branchId) {
    }
}
