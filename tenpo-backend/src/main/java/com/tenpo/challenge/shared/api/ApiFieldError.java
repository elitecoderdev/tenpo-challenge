package com.tenpo.challenge.shared.api;

public record ApiFieldError(
        String field,
        String message
) {
}
