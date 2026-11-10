package com.seatsync.modules.booking.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Result returned by the Mock Payment Facade.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockPaymentResult {

    private boolean success;
    private String paymentReference;
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private String errorMessage;
    private Instant processedAt;
}

