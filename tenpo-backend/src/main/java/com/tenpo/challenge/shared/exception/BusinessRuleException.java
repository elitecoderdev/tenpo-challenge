package com.tenpo.challenge.shared.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends RuntimeException {

    private final HttpStatus status;

    public BusinessRuleException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
