package dev.aiadvent.mentor;

import dev.aiadvent.mentor.model.DeepSeekTransport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;

@SpringBootApplication
public class EngineeringReviewMentorApplication {
    public static void main(String[] args) {
        SpringApplication.run(EngineeringReviewMentorApplication.class, args);
    }

    @Bean
    HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    DeepSeekTransport deepSeekTransport(HttpClient httpClient, ObjectMapper objectMapper) {
        return new DeepSeekTransport(httpClient, objectMapper, System.getenv("DEEPSEEK_API_KEY"));
    }

    @Bean
    DeepSeekClient deepSeekClient(DeepSeekTransport transport, ObjectMapper objectMapper) {
        return new DeepSeekClient(transport, objectMapper);
    }
}
