package com.tenpo.challenge.shared.api;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorFactory {

    public ApiError build(HttpStatus status, String message, String path, List<ApiFieldError> fieldErrors) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                fieldErrors
        );
    }
}
