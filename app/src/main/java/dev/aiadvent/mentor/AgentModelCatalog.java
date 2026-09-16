package dev.aiadvent.mentor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class AgentModelCatalog {
    static final String DEFAULT_KEY = "DEEPSEEK";
    private final AgentModelExecutor deepSeek;
    private final OpenAiAgentModelExecutor openAi;

    AgentModelCatalog(@org.springframework.beans.factory.annotation.Qualifier("deepSeekAgentModelExecutor")
                      AgentModelExecutor deepSeek, OpenAiAgentModelExecutor openAi) {
        this.deepSeek = deepSeek;
        this.openAi = openAi;
    }

    List<Option> options() {
        var options = new ArrayList<Option>();
        options.add(new Option(DEFAULT_KEY, "DEEPSEEK", AgentConfig.defaults().model()));
        for (ModelProfile model : ModelProfile.MODELS) {
            options.add(new Option(model.key(), model.provider(), model.label()));
        }
        return List.copyOf(options);
    }

    Selection resolve(String key) {
        String selected = key == null ? DEFAULT_KEY : key;
        Option option = options().stream().filter(candidate -> candidate.key().equals(selected)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная модель агента."));
        return DEFAULT_KEY.equals(option.key())
                ? new Selection(option, AgentConfig.defaults().model(), deepSeek)
                : new Selection(option, option.key(), openAi);
    }

    record Option(String key, String provider, String label) {
    }

    record Selection(Option option, String model, AgentModelExecutor executor) {
    }
}
