package dev.aiadvent.worker;

import dev.aiadvent.worker.memory.ConversationSummary;
record SummaryGeneration(ConversationSummary summary, TokenMetrics metrics) {
}
