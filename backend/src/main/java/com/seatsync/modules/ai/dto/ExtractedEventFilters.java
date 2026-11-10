package com.seatsync.modules.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Structured search parameters parsed from natural language by Gemini NLP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedEventFilters {

    private String intent;
    private String category;
    private String city;
    private String keyword;
    private BigDecimal maxBudget;
    private BigDecimal minBudget;
    private String timeframe;
}

