package com.tenpo.challenge.shared.api;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ApiFieldError> fieldErrors
) {
}
