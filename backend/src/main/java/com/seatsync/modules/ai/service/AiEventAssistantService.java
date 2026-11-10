package com.seatsync.modules.ai.service;

import com.seatsync.modules.ai.dto.AiChatRequest;
import com.seatsync.modules.ai.dto.AiChatResponse;
import com.seatsync.modules.ai.dto.ExtractedEventFilters;

public interface AiEventAssistantService {

    /**
     * Processes a natural language event query, extracts filters using Gemini NLP,
     * queries authoritative MySQL event data, and synthesizes a grounded answer.
     *
     * @param request the user's message query
     * @param currentUserId authenticated user ID (optional/nullable)
     * @return grounded response with AI summary, extracted filters, and event cards
     */
    AiChatResponse processChat(AiChatRequest request, Long currentUserId);

    /**
     * Extracts structured event filters from natural language using Gemini.
     *
     * @param userMessage natural language text
     * @return structured filters
     */
    ExtractedEventFilters extractFilters(String userMessage);

    /**
     * Returns true if live Google Gemini API is currently connected and active.
     */
    boolean isLiveGeminiActive();
}

