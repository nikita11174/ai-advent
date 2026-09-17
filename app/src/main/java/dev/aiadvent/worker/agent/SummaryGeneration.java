package dev.aiadvent.worker.agent;

import dev.aiadvent.worker.memory.ConversationSummary;
record SummaryGeneration(ConversationSummary summary, TokenMetrics metrics) {
}
