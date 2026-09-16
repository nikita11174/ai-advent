package dev.aiadvent.worker.context;

import dev.aiadvent.worker.dialog.ConversationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Component
public final class ApproximateTokenEstimator {
    public long estimateText(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            return 0;
        }
        return (text.getBytes(StandardCharsets.UTF_8).length + 3L) / 4L;
    }

    public long estimateMessages(List<ConversationContext.Message> messages) {
        Objects.requireNonNull(messages, "messages");
        return messages.stream()
                .mapToLong(message -> estimateText(message.role()) + estimateText(message.content()) + 1)
                .sum();
    }

    public long estimateMessagesWithinLimit(List<ConversationContext.Message> messages, Integer contextTokenLimit) {
        long tokens = estimateMessages(messages);
        if (contextTokenLimit != null && tokens > contextTokenLimit) {
            throw new ContextLimitExceededException(tokens, contextTokenLimit);
        }
        return tokens;
    }
}
