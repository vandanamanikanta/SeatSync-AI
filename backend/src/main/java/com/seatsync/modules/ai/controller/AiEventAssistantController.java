package com.seatsync.modules.ai.controller;

import com.seatsync.common.api.ApiResponse;
import com.seatsync.common.security.SecurityUtils;
import com.seatsync.modules.ai.dto.AiChatRequest;
import com.seatsync.modules.ai.dto.AiChatResponse;
import com.seatsync.modules.ai.service.AiEventAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller exposing the AI Event Discovery Concierge endpoint.
 * Requires authenticated access (CUSTOMER, ORGANIZER, or ADMIN).
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiEventAssistantController {

    private final AiEventAssistantService aiEventAssistantService;
    private final com.seatsync.modules.ai.config.GeminiConfig geminiConfig;

    @PostMapping("/chat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        log.info("Received AI chat request from userId [{}]: {}", currentUserId, request.getMessage());

        AiChatResponse response = aiEventAssistantService.processChat(request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("AI assistant response generated successfully", response));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getStatus() {
        boolean live = aiEventAssistantService.isLiveGeminiActive();
        java.util.Map<String, Object> status = java.util.Map.of(
                "liveGeminiActive", live,
                "model", geminiConfig.getResolvedModelName(),
                "provider", live ? "Google Gemini API (Spring AI)" : "SeatSync Fallback Concierge",
                "timestamp", java.time.Instant.now()
        );
        return ResponseEntity.ok(ApiResponse.success("AI assistant status retrieved", status));
    }
}
