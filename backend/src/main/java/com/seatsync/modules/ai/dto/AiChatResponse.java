package com.seatsync.modules.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Structured response from the AI event assistant, containing the generated
 * natural-language answer, extracted query filters, and grounded database event cards.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    private String response;
    private String intent;
    private ExtractedEventFilters extractedFilters;
    private List<AiEventCardDto> events;
    private int totalMatches;
    private boolean grounded;
    private boolean liveGeminiUsed;
    private String provider;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiEventCardDto {
        private Long id;
        private String title;
        private String slug;
        private String shortDescription;
        private String bannerImageUrl;
        private Instant startTime;
        private BigDecimal basePrice;
        private String categoryName;
        private String categorySlug;
        private String venueName;
        private String city;
    }
}

