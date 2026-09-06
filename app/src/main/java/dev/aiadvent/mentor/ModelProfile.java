package dev.aiadvent.mentor;

import java.math.BigDecimal;
import java.util.List;

record ModelProfile(String key, String label, String provider, String modelId, String modelUrl,
                    Pricing pricing) {
    static final List<ModelProfile> MODELS = List.of(
            profile("WEAK", "Luna", "0.20", "0.02", "0.25", "1.20"),
            profile("MEDIUM", "Terra", "2.00", "0.20", "2.50", "12.00"),
            profile("STRONG", "Sol", "4.00", "0.40", "5.00", "20.00"));

    static ModelProfile resolve(String key) {
        return MODELS.stream().filter(model -> model.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная модель Day 5."));
    }

    private static ModelProfile profile(String key, String name, String input, String cached,
                                        String write, String output) {
        String modelId = "gpt-5.6-" + name.toLowerCase(java.util.Locale.ROOT);
        String url = "https://developers.openai.com/api/docs/models/" + modelId;
        return new ModelProfile(key, "GPT-5.6 " + name, "OPENAI", modelId, url,
                new Pricing("openai-gpt56-standard-2026-09-05", "2026-09-05", "USD", 1_000_000,
                        "default", 272_000, new BigDecimal(input), new BigDecimal(cached),
                        new BigDecimal(write), new BigDecimal(output), List.of(url,
                        "https://developers.openai.com/api/docs/guides/prompt-caching")));
    }

    record Pricing(String id, String checkedAt, String currency, long unit, String serviceTier,
                   long maxInputTokens, BigDecimal inputRate, BigDecimal cachedRate,
                   BigDecimal writeRate, BigDecimal outputRate, List<String> sources) {
        Cost calculate(Usage usage, String actualTier) {
            if (!serviceTier.equals(actualTier)) return unknown("Не подтверждён стандартный service tier.");
            if (usage.inputTokens == null || usage.outputTokens == null || usage.totalTokens == null
                    || usage.cachedInputTokens == null || usage.cacheWriteInputTokens == null) {
                return unknown("Провайдер не вернул все необходимые категории usage.");
            }
            long input = usage.inputTokens, output = usage.outputTokens;
            long cached = usage.cachedInputTokens, write = usage.cacheWriteInputTokens;
            if (input > maxInputTokens) return unknown("Long-context pricing вне snapshot.");
            if (input < 0 || output < 0 || output > 2000 || cached < 0 || write < 0
                    || cached > input || write > input - cached || usage.totalTokens != input + output
                    || (usage.reasoningTokens != null && (usage.reasoningTokens < 0 || usage.reasoningTokens > output))) {
                return unknown("Несогласованные категории usage.");
            }
            BigDecimal amount = inputRate.multiply(BigDecimal.valueOf(input - cached - write))
                    .add(cachedRate.multiply(BigDecimal.valueOf(cached)))
                    .add(writeRate.multiply(BigDecimal.valueOf(write)))
                    .add(outputRate.multiply(BigDecimal.valueOf(output)))
                    .divide(BigDecimal.valueOf(unit));
            return new Cost("ESTIMATED", amount.toPlainString(), null, this);
        }

        Cost unknown(String reason) { return new Cost("UNKNOWN", null, reason, this); }
    }

    record Usage(Long inputTokens, Long outputTokens, Long totalTokens, Long cachedInputTokens,
                 Long cacheWriteInputTokens, Long reasoningTokens) { }
    record Cost(String status, String amount, String reason, Pricing snapshot) { }
}
