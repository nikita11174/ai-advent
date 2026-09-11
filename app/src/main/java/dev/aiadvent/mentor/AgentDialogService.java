package dev.aiadvent.mentor;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
class AgentDialogService {
    private final DialogStore dialogs;
    private final DeepSeekClient client;
    private final ConcurrentHashMap<UUID, EngineeringReviewAgent> agents = new ConcurrentHashMap<>();

    AgentDialogService(DialogStore dialogs, DeepSeekClient client) {
        this.dialogs = dialogs;
        this.client = client;
    }

    String reply(UUID dialogId, String input) throws IOException, DeepSeekException {
        EngineeringReviewAgent agent = agents.get(dialogId);
        if (agent == null) {
            dialogs.load(dialogId.toString());
            agent = agents.computeIfAbsent(dialogId, id -> new EngineeringReviewAgent(AgentConfig.defaults(), client));
        }
        return agent.reply(input);
    }
}
