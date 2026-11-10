package com.seatsync.modules.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback ChatModel implementation used for testing and local development
 * when GEMINI_API_KEY is not configured or when offline.
 * Emulates Gemini's structured JSON entity extraction and grounded response generation.
 */
@Slf4j
public class FallbackGeminiChatModel implements ChatModel {

    @Override
    public ChatResponse call(Prompt prompt) {
        StringBuilder fullPromptBuilder = new StringBuilder();
        for (Message msg : prompt.getInstructions()) {
            fullPromptBuilder.append(msg.getText()).append("\n");
        }
        String promptText = fullPromptBuilder.toString();

        String answer;
        if (promptText.contains("Extract structured search parameters") || promptText.contains("Return ONLY a valid JSON object")) {
            answer = handleExtraction(promptText);
        } else {
            answer = handleGroundedResponse(promptText);
        }

        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }

    private String handleExtraction(String promptText) {
        Pattern qPattern = Pattern.compile("User Query:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher qMatcher = qPattern.matcher(promptText);
        String userQuery = qMatcher.find() ? qMatcher.group(1) : promptText;
        String lower = userQuery.toLowerCase(Locale.ROOT);

        String intent = "SEARCH_EVENTS";
        if (lower.contains("similar") || lower.contains("recommend")) {
            intent = "RECOMMEND_SIMILAR";
        } else if (lower.contains("under") || lower.contains("price") || lower.contains("cheap") || lower.contains("budget")) {
            intent = "PRICE_QUERY";
        } else if (lower.contains("weekend") || lower.contains("tomorrow") || lower.contains("today") || lower.contains("date")) {
            intent = "DATE_QUERY";
        }

        String category = null;
        if (lower.contains("music") || lower.contains("concert") || lower.contains("band") || lower.contains("rock")) {
            category = "concerts-music";
        } else if (lower.contains("comedy") || lower.contains("standup") || lower.contains("stand-up") || lower.contains("comic")) {
            category = "standup-comedy";
        } else if (lower.contains("sport") || lower.contains("cricket") || lower.contains("football") || lower.contains("fitness")) {
            category = "sports-fitness";
        } else if (lower.contains("theatre") || lower.contains("theater") || lower.contains("play") || lower.contains("drama")) {
            category = "theatre-arts";
        } else if (lower.contains("tech") || lower.contains("conference") || lower.contains("summit") || lower.matches(".*\\bai\\b.*") || lower.contains("artificial intelligence")) {
            category = "tech-conferences";
        } else if (lower.contains("edm") || lower.contains("dj") || lower.contains("dance")) {
            category = "edm-festivals-2b8e2e";
        }

        String city = null;
        if (lower.contains("mumbai")) {
            city = "Mumbai";
        } else if (lower.contains("bengaluru") || lower.contains("bangalore")) {
            city = "Bengaluru";
        } else if (lower.contains("hyderabad")) {
            city = "Hyderabad";
        } else if (lower.contains("delhi") || lower.contains("new delhi")) {
            city = "Delhi";
        } else if (lower.contains("pune")) {
            city = "Pune";
        } else if (lower.contains("chennai")) {
            city = "Chennai";
        } else if (lower.contains("kolkata")) {
            city = "Kolkata";
        }

        String timeframe = null;
        if (lower.contains("this weekend") || lower.contains("weekend")) {
            timeframe = "THIS_WEEKEND";
        } else if (lower.contains("today")) {
            timeframe = "TODAY";
        } else if (lower.contains("tomorrow")) {
            timeframe = "TOMORROW";
        } else if (lower.contains("upcoming")) {
            timeframe = "UPCOMING";
        }

        Double maxBudget = null;
        // Match numbers (including with commas like 2,000) after under, below, max, <=, or currency symbols
        Pattern budgetPattern = Pattern.compile("(?:under|below|max|less than|up to|[₹]|rs\\.?|inr)\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = budgetPattern.matcher(lower);
        if (matcher.find()) {
            try {
                String cleanNum = matcher.group(1).replace(",", "").trim();
                maxBudget = Double.parseDouble(cleanNum);
            } catch (NumberFormatException ignored) {}
        } else {
            // General price check
            Pattern numPattern = Pattern.compile("([0-9,]{3,7})");
            Matcher numMatcher = numPattern.matcher(lower);
            if (numMatcher.find()) {
                try {
                    String cleanNum = numMatcher.group(1).replace(",", "").trim();
                    maxBudget = Double.parseDouble(cleanNum);
                } catch (NumberFormatException ignored) {}
            }
        }

        String keyword = null;
        if (lower.contains("rock")) {
            keyword = "rock";
        } else if (lower.contains("audit")) {
            keyword = "audit";
        }

        StringBuilder json = new StringBuilder("{");
        json.append("\"intent\": \"").append(intent).append("\", ");
        json.append("\"category\": ").append(category != null ? "\"" + category + "\"" : "null").append(", ");
        json.append("\"city\": ").append(city != null ? "\"" + city + "\"" : "null").append(", ");
        json.append("\"keyword\": ").append(keyword != null ? "\"" + keyword + "\"" : "null").append(", ");
        json.append("\"maxBudget\": ").append(maxBudget != null ? maxBudget : "null").append(", ");
        json.append("\"minBudget\": null, ");
        json.append("\"timeframe\": ").append(timeframe != null ? "\"" + timeframe + "\"" : "null");
        json.append("}");

        return json.toString();
    }

    private String handleGroundedResponse(String promptText) {
        if (promptText.contains("CATALOG IS EMPTY") || promptText.contains("No matching events found")) {
            return "I searched our catalog, but there are currently no events matching your exact criteria. " +
                   "However, you can explore other upcoming live concerts in Mumbai on SeatSync AI!";
        }

        if (promptText.contains("Audit World Tour") && promptText.contains("Rock Revolution")) {
            return "I found exciting events matching your request! In Mumbai, you can check out 'Rock Revolution Live 2026 - Official Tour' starting at ₹1,999.00 and 'Audit World Tour 2026 - Premium Edition' at Mumbai Grand Arena. You can select your seats in VIP, Platinum, or Gold tiers and book directly on SeatSync AI!";
        } else if (promptText.contains("Rock Revolution")) {
            return "I found 'Rock Revolution Live 2026 - Official Tour' at Mumbai Grand Arena! Tickets start at ₹1,999.00 with live real-time seat selection available across VIP, Platinum, and Gold tiers. You can book directly using the links below.";
        } else if (promptText.contains("Audit World Tour")) {
            return "I found 'Audit World Tour 2026 - Premium Edition' at Mumbai Grand Arena! Tickets start at ₹2,999.00. You can browse available seating and complete your booking right here on SeatSync AI.";
        }

        return "Here are the events currently available on SeatSync AI matching your search. Click on any event card below to view details and select your live seats!";
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return null;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }
}
