package dev.aiadvent.mentor;

record ReviewControls(
        int maxTokens,
        int maxFindings,
        int summaryMaxWords,
        int reasonMaxWords,
        int recommendationMaxWords,
        String terminationInstruction) {

    static final String DEFAULT_TERMINATION_INSTRUCTION = "Return exactly one JSON object. "
            + "Stop immediately after the final closing brace. "
            + "Do not add Markdown, explanations or text outside the JSON object.";

    static ReviewControls defaults() {
        return new ReviewControls(600, 3, 30, 30, 30, DEFAULT_TERMINATION_INSTRUCTION);
    }

    ReviewControls validated() {
        requireRange("maxTokens", maxTokens, 100, 2000);
        requireRange("maxFindings", maxFindings, 0, 10);
        requireRange("summaryMaxWords", summaryMaxWords, 1, 100);
        requireRange("reasonMaxWords", reasonMaxWords, 1, 100);
        requireRange("recommendationMaxWords", recommendationMaxWords, 1, 100);
        if (terminationInstruction == null
                || terminationInstruction.trim().isEmpty()
                || terminationInstruction.trim().length() > 1000) {
            throw new IllegalArgumentException("terminationInstruction must contain 1 to 1000 characters.");
        }
        return new ReviewControls(maxTokens, maxFindings, summaryMaxWords, reasonMaxWords,
                recommendationMaxWords, terminationInstruction.trim());
    }

    private static void requireRange(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ".");
        }
    }
}
