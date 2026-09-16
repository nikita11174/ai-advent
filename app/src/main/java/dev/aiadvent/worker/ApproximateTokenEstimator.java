package dev.aiadvent.worker;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Component
final class ApproximateTokenEstimator {
    long estimateText(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            return 0;
        }
        return (text.getBytes(StandardCharsets.UTF_8).length + 3L) / 4L;
    }

    long estimateMessages(List<ConversationContext.Message> messages) {
        Objects.requireNonNull(messages, "messages");
        return messages.stream()
                .mapToLong(message -> estimateText(message.role()) + estimateText(message.content()) + 1)
                .sum();
    }

    long estimateMessagesWithinLimit(List<ConversationContext.Message> messages, Integer contextTokenLimit) {
        long tokens = estimateMessages(messages);
        if (contextTokenLimit != null && tokens > contextTokenLimit) {
            throw new ConversationAgent.ContextLimitExceededException(tokens, contextTokenLimit);
        }
        return tokens;
    }
}
