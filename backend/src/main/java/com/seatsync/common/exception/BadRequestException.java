package com.seatsync.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when request arguments or payload logic are invalid (400).
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    public BadRequestException(String message, String errorCode) {
        super(message, HttpStatus.BAD_REQUEST, errorCode);
    }
}

