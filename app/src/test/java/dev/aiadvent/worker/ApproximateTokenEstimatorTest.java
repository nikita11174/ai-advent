package dev.aiadvent.worker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApproximateTokenEstimatorTest {
    private final ApproximateTokenEstimator estimator = new ApproximateTokenEstimator();

    @Test
    void estimatesUtf8TextAndMessageFramingDeterministically() {
        assertEquals(0, estimator.estimateText(""));
        assertEquals(1, estimator.estimateText("abcd"));
        assertEquals(2, estimator.estimateText("abcde"));
        assertEquals(3, estimator.estimateText("Сатурн"));

        assertEquals(3, estimator.estimateMessages(List.of(
                new ConversationContext.Message("user", "abcd"))));
    }
}
