package com.seatsync.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when mock payment processing fails or is declined.
 */
public class PaymentFailedException extends ApiException {

    public PaymentFailedException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED");
    }

    public PaymentFailedException(String message, String errorCode) {
        super(message, HttpStatus.PAYMENT_REQUIRED, errorCode);
    }
}

