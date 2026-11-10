package com.seatsync.modules.booking.payment;

import com.seatsync.common.exception.PaymentFailedException;
import com.seatsync.modules.booking.entity.Booking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Mock Payment Facade for simulating payment transactions in development and testing.
 * Strictly a mock implementation; does NOT integrate with real payment processors.
 */
@Slf4j
@Component
public class MockPaymentFacade {

    private static final String FAILURE_TRIGGER = "FAIL";
    private static final String DECLINED_CARD_SUFFIX = "0002";

    /**
     * Executes mock payment authorization and capture.
     *
     * @param booking       the target booking
     * @param paymentMethod payment method description or test card token
     * @return MockPaymentResult confirming payment details
     * @throws PaymentFailedException if payment is configured to fail or simulated decline occurs
     */
    public MockPaymentResult processPayment(Booking booking, String paymentMethod) {
        log.info("[MOCK PAYMENT FACADE] Processing payment for booking {} ({}) of amount {} {}",
                booking.getId(), booking.getBookingReference(), booking.getTotalAmount(), booking.getCurrency());

        // Check for simulated failure trigger
        if (paymentMethod != null && (paymentMethod.toUpperCase().contains(FAILURE_TRIGGER)
                || paymentMethod.endsWith(DECLINED_CARD_SUFFIX))) {
            log.warn("[MOCK PAYMENT FACADE] Simulated payment failure triggered for booking {}", booking.getId());
            throw new PaymentFailedException(
                    "Payment authorization failed: Card declined by issuing bank (Simulated Mock Decline)",
                    "PAYMENT_DECLINED"
            );
        }

        String paymentRef = "MOCK-PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String txnId = "TXN-MOCK-" + UUID.randomUUID().toString();

        log.info("[MOCK PAYMENT FACADE] Mock payment successful: reference={}, transactionId={}",
                paymentRef, txnId);

        return MockPaymentResult.builder()
                .success(true)
                .paymentReference(paymentRef)
                .transactionId(txnId)
                .amount(booking.getTotalAmount())
                .currency(booking.getCurrency())
                .processedAt(Instant.now())
                .build();
    }
}

