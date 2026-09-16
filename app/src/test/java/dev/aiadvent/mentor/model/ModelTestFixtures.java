package dev.aiadvent.mentor.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@TestConfiguration
public class ModelTestFixtures {
    @Bean
    AgentModelExecutor openAiAgentModelExecutor(OpenAiResponsesClient client) {
        return new OpenAiAgentModelExecutor(client);
    }

    public static void registerExecutors(AnnotationConfigApplicationContext context) {
        context.register(DeepSeekAgentModelExecutor.class, OpenAiAgentModelExecutor.class);
    }

    public static AgentModelExecutor deepSeekExecutor(DeepSeekTransport transport) {
        return new DeepSeekAgentModelExecutor(transport);
    }

    public static AgentModelExecutor openAiExecutor(OpenAiResponsesClient client) {
        return new OpenAiAgentModelExecutor(client);
    }

    public static OpenAiResponsesClient openAiClient(ObjectMapper json, HttpClient http, URI endpoint,
                                                    Duration timeout, String apiKey) {
        return new OpenAiResponsesClient(json, http, endpoint, timeout, apiKey);
    }

    public static DeepSeekTransport.Completion complete(DeepSeekTransport transport, AgentModelRequest request)
            throws DeepSeekException {
        return transport.complete(request);
    }

    public static OpenAiResponsesClient.Result complete(OpenAiResponsesClient client, ModelProfile model,
                                                        List<AgentModelMessage> messages,
                                                        Double temperature, Integer maxTokens) {
        return client.complete(model, messages, temperature, maxTokens);
    }
}
