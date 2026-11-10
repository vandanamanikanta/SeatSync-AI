package com.seatsync.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated user lacks permission to access a resource (403).
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public ForbiddenException(String message, String errorCode) {
        super(message, HttpStatus.FORBIDDEN, errorCode);
    }
}

