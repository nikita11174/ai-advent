package dev.aiadvent.worker.model;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AgentModelCatalog {
    public static final String DEFAULT_KEY = "DEEPSEEK";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private final AgentModelExecutor deepSeek;
    private final AgentModelExecutor openAi;

    public AgentModelCatalog(@org.springframework.beans.factory.annotation.Qualifier("deepSeekAgentModelExecutor")
                      AgentModelExecutor deepSeek, @org.springframework.beans.factory.annotation.Qualifier("openAiAgentModelExecutor")
                      AgentModelExecutor openAi) {
        this.deepSeek = deepSeek;
        this.openAi = openAi;
    }

    public List<Option> options() {
        var options = new ArrayList<Option>();
        options.add(new Option(DEFAULT_KEY, "DEEPSEEK", DEFAULT_MODEL));
        for (ModelProfile model : ModelProfile.MODELS) {
            options.add(new Option(model.key(), model.provider(), model.label()));
        }
        return List.copyOf(options);
    }

    public Selection resolve(String key) {
        String selected = key == null ? DEFAULT_KEY : key;
        Option option = options().stream().filter(candidate -> candidate.key().equals(selected)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная модель агента."));
        return DEFAULT_KEY.equals(option.key())
                ? new Selection(option, DEFAULT_MODEL, deepSeek)
                : new Selection(option, option.key(), openAi);
    }

    public record Option(String key, String provider, String label) {
    }

    public record Selection(Option option, String model, AgentModelExecutor executor) {
    }
}
