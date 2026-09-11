package dev.aiadvent.mentor;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
class AgentDialogService {
    private final DialogStore dialogs;
    private final DeepSeekClient client;
    private final AgentHistoryStore histories;
    private final ConcurrentHashMap<UUID, EngineeringReviewAgent> agents = new ConcurrentHashMap<>();

    AgentDialogService(DialogStore dialogs, DeepSeekClient client, AgentHistoryStore histories) {
        this.dialogs = dialogs;
        this.client = client;
        this.histories = histories;
    }

    String reply(UUID dialogId, String input) throws IOException, DeepSeekException {
        EngineeringReviewAgent agent = agents.get(dialogId);
        if (agent == null) {
            dialogs.load(dialogId.toString());
            ConversationContext context = histories.load(dialogId)
                    .map(ConversationContext::new)
                    .orElseGet(() -> new ConversationContext(AgentConfig.defaults().systemPrompt()));
            EngineeringReviewAgent created = new EngineeringReviewAgent(dialogId, AgentConfig.defaults(), context,
                    client, histories);
            EngineeringReviewAgent existing = agents.putIfAbsent(dialogId, created);
            agent = existing == null ? created : existing;
        }
        return agent.reply(input);
    }
}
