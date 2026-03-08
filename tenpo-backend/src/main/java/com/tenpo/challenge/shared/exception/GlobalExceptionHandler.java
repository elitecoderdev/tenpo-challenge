package com.tenpo.challenge.shared.exception;

import com.tenpo.challenge.shared.api.ApiError;
import com.tenpo.challenge.shared.api.ApiErrorFactory;
import com.tenpo.challenge.shared.api.ApiFieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ApiErrorFactory apiErrorFactory;

    public GlobalExceptionHandler(ApiErrorFactory apiErrorFactory) {
        this.apiErrorFactory = apiErrorFactory;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException exception, HttpServletRequest request) {
        return buildResponse(exception.getStatus(), exception.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed for the request body.",
                request.getRequestURI(),
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ApiFieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed for request parameters.", request.getRequestURI(), fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Request payload could not be parsed. Check field formats and required values.",
                request.getRequestURI(),
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpectedError(Exception exception, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred.",
                request.getRequestURI(),
                List.of()
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String message,
            String path,
            List<ApiFieldError> fieldErrors
    ) {
        return ResponseEntity.status(status).body(apiErrorFactory.build(status, message, path, fieldErrors));
    }

    private ApiFieldError mapFieldError(FieldError fieldError) {
        return new ApiFieldError(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
