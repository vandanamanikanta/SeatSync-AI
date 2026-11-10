package com.seatsync.modules.ai.config;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

/**
 * Spring AI configuration for Google Gemini integration.
 * Connects securely to the Google Gemini API when an API key is provided,
 * while offering resilient fallback to prevent startup failures when no key is present.
 */
@Slf4j
@Configuration
public class GeminiConfig {

    @Value("${spring.ai.google.genai.api-key:${GEMINI_API_KEY:}}")
    private String apiKey;

    @Value("${app.gemini.model:${GEMINI_MODEL:gemini-2.5-flash}}")
    private String modelName;

    @Value("${app.gemini.timeout-seconds:30}")
    private int timeoutSeconds;

    private boolean resolved = false;

    public synchronized void resolveConfig() {
        if (resolved) return;
        resolved = true;

        // 1. Try OS environment variables first
        String envKey = System.getenv("GEMINI_API_KEY");
        if (!StringUtils.hasText(envKey)) {
            envKey = System.getenv("GOOGLE_API_KEY");
        }
        if (StringUtils.hasText(envKey)) {
            this.apiKey = envKey.trim();
        }

        String envModel = System.getenv("GEMINI_MODEL");
        if (StringUtils.hasText(envModel)) {
            this.modelName = envModel.trim();
        }

        // 2. If key or custom model not in OS env, load from .env files
        loadConfigFromPotentialEnvFiles();
    }

    private void loadConfigFromPotentialEnvFiles() {
        List<java.nio.file.Path> potentialPaths = List.of(
                java.nio.file.Paths.get("d:/SeatSync-AI/.env"),
                java.nio.file.Paths.get("d:/SeatSync-AI/backend/.env"),
                java.nio.file.Paths.get(".env"),
                java.nio.file.Paths.get("../.env"),
                java.nio.file.Paths.get(System.getProperty("user.home", ""), ".env")
        );
        for (java.nio.file.Path p : potentialPaths) {
            try {
                if (java.nio.file.Files.exists(p)) {
                    List<String> lines = java.nio.file.Files.readAllLines(p);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("#") || trimmed.isEmpty()) continue;
                        if ((trimmed.startsWith("GEMINI_API_KEY=") || trimmed.startsWith("GOOGLE_API_KEY=")) && !StringUtils.hasText(this.apiKey)) {
                            int idx = trimmed.indexOf('=');
                            String val = trimmed.substring(idx + 1).trim();
                            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (StringUtils.hasText(val) && !"PASTE_YOUR_API_KEY_HERE".equalsIgnoreCase(val)) {
                                this.apiKey = val;
                                log.info("Successfully detected Gemini API key from environment configuration file: {}", p.getFileName());
                            }
                        }
                        if (trimmed.startsWith("GEMINI_MODEL=")) {
                            int idx = trimmed.indexOf('=');
                            String val = trimmed.substring(idx + 1).trim();
                            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (StringUtils.hasText(val)) {
                                this.modelName = val;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public boolean isLiveGeminiConfigured() {
        resolveConfig();
        return StringUtils.hasText(apiKey) &&
               !apiKey.isBlank() &&
               !"dummy".equalsIgnoreCase(apiKey) &&
               !"placeholder".equalsIgnoreCase(apiKey) &&
               !"PASTE_YOUR_API_KEY_HERE".equalsIgnoreCase(apiKey);
    }

    public String getResolvedModelName() {
        resolveConfig();
        return modelName;
    }

    @Bean
    @Primary
    public ChatModel chatModel() {
        resolveConfig();
        if (isLiveGeminiConfigured()) {
            try {
                log.info("Initializing live Google Gemini ChatModel with model '{}'", modelName);
                com.google.genai.Client client = com.google.genai.Client.builder()
                        .apiKey(apiKey.trim())
                        .build();

                GoogleGenAiChatOptions defaultOptions = GoogleGenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(0.2)
                        .maxOutputTokens(2048)
                        .build();

                return GoogleGenAiChatModel.builder()
                        .genAiClient(client)
                        .defaultOptions(defaultOptions)
                        .build();
            } catch (Exception e) {
                log.error("Failed to construct live GoogleGenAiChatModel: {}. Reverting to fallback model.", e.getMessage());
            }
        } else {
            log.info("GEMINI_API_KEY is not configured in environment. Using FallbackGeminiChatModel for offline testing.");
        }
        return new FallbackGeminiChatModel();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("You are the SeatSync AI Intelligent Event Assistant. Help users discover live events.")
                .build();
    }
}

