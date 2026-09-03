package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            System.out.println("Engineering Review Mentor - Day 1");
            System.out.println("Paste Java code or engineering question, then send EOF:");

            String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            DeepSeekClient client = new DeepSeekClient(
                    HttpClient.newHttpClient(), new ObjectMapper(), System.getenv("DEEPSEEK_API_KEY"));

            System.out.println();
            System.out.println("Analysis:");
            System.out.println(client.analyze(input));
        } catch (DeepSeekException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error: Could not read input.");
            System.exit(1);
        }
    }
}
