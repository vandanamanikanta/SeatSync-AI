package com.seatsync.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation results in a resource or business state conflict (409).
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "RESOURCE_CONFLICT");
    }

    public ConflictException(String message, String errorCode) {
        super(message, HttpStatus.CONFLICT, errorCode);
    }
}

