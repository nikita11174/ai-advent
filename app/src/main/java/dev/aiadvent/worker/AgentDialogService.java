package dev.aiadvent.worker;

import dev.aiadvent.worker.dialog.AgentBranchStore;
import dev.aiadvent.worker.dialog.AgentHistoryStore;
import dev.aiadvent.worker.dialog.ConversationContext;
import dev.aiadvent.worker.dialog.DialogStore;
import dev.aiadvent.worker.memory.AgentMemory;
import dev.aiadvent.worker.memory.AgentMemoryStore;
import dev.aiadvent.worker.memory.AgentSummaryStore;
import dev.aiadvent.worker.memory.StickyFactsStore;
import dev.aiadvent.worker.context.ApproximateTokenEstimator;
import dev.aiadvent.worker.context.ContextMode;
import dev.aiadvent.worker.context.FullContextPolicy;
import dev.aiadvent.worker.context.SlidingWindowContextPolicy;
import dev.aiadvent.worker.context.StickyFactsContextPolicy;
import dev.aiadvent.worker.context.SummaryRecentContextPolicy;
import dev.aiadvent.worker.model.AgentModelCatalog;
import dev.aiadvent.worker.model.AgentModelExecutor;
import dev.aiadvent.worker.model.ModelExecutionException;

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
    private final ConcurrentHashMap<AgentKey, ConversationAgent> agents = new ConcurrentHashMap<>();

    AgentDialogService(DialogStore dialogs, AgentModelExecutor executor, AgentHistoryStore histories,
                       ApproximateTokenEstimator tokenEstimator,
                       AgentSummaryStore summaries, ConversationSummaryService summaryService,
                       StickyFactsStore factsStore, StickyFactsService factsService,
                       AgentBranchStore branches, AgentMemoryStore memories,
                       @Value("${mentor.agent.context-token-limit:0}") int contextTokenLimit) {
        this(dialogs, histories, tokenEstimator, summaries, summaryService, factsStore, factsService,
                branches, memories, contextTokenLimit, new AgentModelCatalog(executor, null));
    }

    @org.springframework.beans.factory.annotation.Autowired
    AgentDialogService(DialogStore dialogs, AgentHistoryStore histories,
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
            throws IOException, ModelExecutionException {
        return reply(dialogId, input, mode, recentMessageCount, null);
    }

    AgentReply reply(UUID dialogId, String input, ContextMode mode, Integer recentMessageCount, String branchId)
            throws IOException, ModelExecutionException {
        return reply(dialogId, input, mode, recentMessageCount, branchId, null);
    }

    AgentReply reply(UUID dialogId, String input, ContextMode mode, Integer recentMessageCount, String branchId,
                     UUID taskId) throws IOException, ModelExecutionException {
        return reply(dialogId, input, mode, recentMessageCount, branchId, taskId, null);
    }

    AgentReply reply(UUID dialogId, String input, ContextMode mode, Integer recentMessageCount, String branchId,
                     UUID taskId, String agentModelKey) throws IOException, ModelExecutionException {
        AgentModelCatalog.Selection model = models.resolve(agentModelKey);
        if (branchId != null && (mode != null && mode != ContextMode.FULL)) {
            throw new IllegalArgumentException("Branch messages use FULL context only.");
        }
        AgentKey key = new AgentKey(dialogId, branchId);
        ConversationAgent agent = agents.get(key);
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
            ConversationAgent created = new ConversationAgent(dialogId, defaultConfig, context,
                    models.resolve(null).executor(), histories, tokenEstimator, summaries, summaryService,
                    factsStore, factsService, new FullContextPolicy(), new SummaryRecentContextPolicy(),
                    new SlidingWindowContextPolicy(), new StickyFactsContextPolicy(), branches, branchId);
            ConversationAgent existing = agents.putIfAbsent(key, created);
            agent = existing == null ? created : existing;
        }
        AgentMemory.Snapshot memory = memories.load(dialogId, taskId);
        return agent.reply(input, mode, recentMessageCount, memory,
                model.executor(), defaultConfig.withModel(model.model()));
    }

    AgentReply reply(UUID dialogId, String input) throws IOException, ModelExecutionException {
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
